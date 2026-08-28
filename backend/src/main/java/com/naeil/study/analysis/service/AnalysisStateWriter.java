package com.naeil.study.analysis.service;

import com.naeil.study.analysis.client.dto.AiStudyContext;
import com.naeil.study.analysis.exception.AnalysisAlreadyRunningException;
import com.naeil.study.analysis.exception.ExamInfoRequiredException;
import com.naeil.study.analysis.exception.NoParsedDocumentException;
import com.naeil.study.analysis.service.AnalysisTarget.AnalysisDocument;
import com.naeil.study.analysis.validation.ValidatedTopic;
import com.naeil.study.curriculum.repository.CurriculumRepository;
import com.naeil.study.curriculum.repository.StudyStepRepository;
import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentStatus;
import com.naeil.study.document.repository.DocumentRepository;
import com.naeil.study.quiz.repository.QuizRepository;
import com.naeil.study.quiz.repository.QuizResultRepository;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.repository.StudySessionRepository;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.studycontext.entity.StudyContext;
import com.naeil.study.studycontext.repository.StudyContextRepository;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.repository.TopicRepository;
import com.naeil.study.wronganswer.repository.WrongAnswerSummaryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분석 과정의 DB 작업만 담당한다.
 *
 * <p>AI 호출은 수십 초가 걸릴 수 있다. 그 시간 동안 트랜잭션을 잡고 있으면 커넥션이 묶인다.
 * 그래서 상태 변경을 짧은 트랜잭션 여러 개로 나눴다.
 *
 * <pre>
 * beginAnalysis()   tx  검증 → ANALYZING 으로 바꾸고 분석 대상을 복사해 나온다
 *      ↓
 * (트랜잭션 밖)         Chunking + AI 호출 + 응답 검증
 *      ↓
 * completeAnalysis() tx  기존 Topic 삭제 + 새 Topic 저장 + READY
 * failAnalysis()     tx  ANALYSIS_FAILED
 * </pre>
 *
 * <p>{@link AnalysisService}가 자기 메서드를 호출하면 프록시를 거치지 않아 트랜잭션이
 * 적용되지 않는다. 그래서 별도 빈으로 분리했다.
 */
@Service
public class AnalysisStateWriter {

    private final StudySessionRepository studySessionRepository;
    private final DocumentRepository documentRepository;
    private final StudyContextRepository studyContextRepository;
    private final TopicRepository topicRepository;
    private final QuizResultRepository quizResultRepository;
    private final QuizRepository quizRepository;
    private final StudyStepRepository studyStepRepository;
    private final CurriculumRepository curriculumRepository;
    private final WrongAnswerSummaryRepository wrongAnswerSummaryRepository;
    private final Clock clock;

    public AnalysisStateWriter(
            StudySessionRepository studySessionRepository,
            DocumentRepository documentRepository,
            StudyContextRepository studyContextRepository,
            TopicRepository topicRepository,
            QuizResultRepository quizResultRepository,
            QuizRepository quizRepository,
            StudyStepRepository studyStepRepository,
            CurriculumRepository curriculumRepository,
            WrongAnswerSummaryRepository wrongAnswerSummaryRepository,
            Clock clock
    ) {
        this.studySessionRepository = studySessionRepository;
        this.documentRepository = documentRepository;
        this.studyContextRepository = studyContextRepository;
        this.topicRepository = topicRepository;
        this.quizResultRepository = quizResultRepository;
        this.quizRepository = quizRepository;
        this.studyStepRepository = studyStepRepository;
        this.curriculumRepository = curriculumRepository;
        this.wrongAnswerSummaryRepository = wrongAnswerSummaryRepository;
        this.clock = clock;
    }

    /**
     * 분석 시작 조건을 확인하고 세션을 {@code ANALYZING}으로 바꾼다.
     *
     * @param sessionId 세션 조회와 접근시각 갱신은 호출자가 이미 마친 뒤여야 한다
     * @throws ExamInfoRequiredException        시험 정보가 없음
     * @throws NoParsedDocumentException        텍스트 추출을 마친 자료가 없음
     * @throws AnalysisAlreadyRunningException  이미 분석 중
     */
    @Transactional
    public AnalysisTarget beginAnalysis(UUID sessionId) {
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(SessionNotFoundException::new);

        if (session.isAnalyzing()) {
            throw new AnalysisAlreadyRunningException();
        }
        if (!session.hasExamInfo() || session.getSubject() == null) {
            throw new ExamInfoRequiredException();
        }

        List<Document> parsed = documentRepository
                .findAllByStudySessionIdAndStatusOrderByCreatedAtAsc(sessionId, DocumentStatus.PARSED);
        if (parsed.isEmpty()) {
            throw new NoParsedDocumentException();
        }

        session.startAnalyzing(now());

        return new AnalysisTarget(
                sessionId,
                session.getSubject(),
                toAiStudyContext(sessionId),
                toAnalysisDocuments(parsed));
    }

    /**
     * 분석 결과를 저장하고 세션을 {@code READY}로 바꾼다.
     *
     * <p>기존 Topic을 지우고 새로 넣는다. 분석 결과는 통째로 갈아 끼우는 값이라
     * 재분석 때 이전 결과가 섞이면 안 된다.
     *
     * <p><b>Topic 에서 파생된 것들을 먼저 지운다.</b> 학습 계획·퀴즈·오답 요약은 전부
     * 옛 Topic 을 가리키므로 재분석과 함께 무효가 된다. 지우지 않으면 FK 제약 때문에
     * Topic 삭제 자체가 실패하고(실제로 겪은 500), 남겨 둔들 새 Topic 과 무관한
     * 낡은 계획이 화면에 살아남는다. 삭제 순서는 FK 의존의 역순이다.
     */
    @Transactional
    public List<Topic> completeAnalysis(UUID sessionId, List<ValidatedTopic> validatedTopics) {
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(SessionNotFoundException::new);
        LocalDateTime now = now();

        // 답안 → 퀴즈 → 학습 단계 → 계획 → 오답 요약 → Topic 순서로 지운다
        quizResultRepository.deleteAllByStudySessionId(sessionId);
        quizRepository.deleteAllByTopicStudySessionId(sessionId);
        studyStepRepository.deleteAllByCurriculumStudySessionId(sessionId);
        curriculumRepository.deleteAllByStudySessionId(sessionId);
        wrongAnswerSummaryRepository.deleteAllByStudySessionId(sessionId);
        topicRepository.deleteAllByStudySessionId(sessionId);
        topicRepository.flush();

        // 진행 중이던 단계 표시도 옛 계획의 것이다. 새 계획은 처음부터 시작한다.
        session.clearCurrentStep(now);

        List<Topic> topics = new ArrayList<>(validatedTopics.size());
        for (ValidatedTopic validated : validatedTopics) {
            topics.add(Topic.create(
                    session,
                    validated.title(),
                    validated.summary(),
                    validated.keyPoints(),
                    validated.importance(),
                    validated.estimatedStudyMinutes(),
                    validated.professorEmphasisMatched(),
                    validated.pastExamMatched(),
                    validated.weakAreaMatched(),
                    validated.mustStudyMatched(),
                    validated.sourceDocumentIds(),
                    validated.topicOrder(),
                    now));
        }
        List<Topic> saved = topicRepository.saveAllAndFlush(topics);
        session.markReady(now);
        return saved;
    }

    /**
     * 분석 실패를 기록한다.
     *
     * <p>강의자료와 학습 맥락은 지우지 않는다. 사용자가 다시 분석을 요청할 수 있어야 한다.
     */
    @Transactional
    public void failAnalysis(UUID sessionId) {
        studySessionRepository.findById(sessionId)
                .ifPresent(session -> session.markAnalysisFailed(now()));
    }

    private AiStudyContext toAiStudyContext(UUID sessionId) {
        return studyContextRepository.findByStudySessionId(sessionId)
                .map(context -> new AiStudyContext(
                        context.getProfessorEmphasis(),
                        context.getPastExamInfo(),
                        context.getWeakAreas(),
                        context.getMustStudyAreas()))
                .orElseGet(AiStudyContext::empty);
    }

    /**
     * 문서에 AI용 참조값을 붙인다.
     *
     * <p>{@code DOC_1}, {@code DOC_2} 처럼 짧은 값을 준다. 내부 UUID를 주면
     * LLM이 그럴듯한 UUID를 지어내 존재하지 않는 문서를 가리킬 수 있다.
     */
    private List<AnalysisDocument> toAnalysisDocuments(List<Document> documents) {
        List<AnalysisDocument> analysisDocuments = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            analysisDocuments.add(new AnalysisDocument(
                    document.getId(),
                    "DOC_" + (i + 1),
                    document.getOriginalFileName(),
                    document.getExtractedText()));
        }
        return analysisDocuments;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
