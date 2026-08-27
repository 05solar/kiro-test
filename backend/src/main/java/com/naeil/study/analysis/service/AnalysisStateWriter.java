package com.naeil.study.analysis.service;

import com.naeil.study.analysis.client.dto.AiStudyContext;
import com.naeil.study.analysis.exception.AnalysisAlreadyRunningException;
import com.naeil.study.analysis.exception.ExamInfoRequiredException;
import com.naeil.study.analysis.exception.NoParsedDocumentException;
import com.naeil.study.analysis.service.AnalysisTarget.AnalysisDocument;
import com.naeil.study.analysis.validation.ValidatedTopic;
import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentStatus;
import com.naeil.study.document.repository.DocumentRepository;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.repository.StudySessionRepository;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.studycontext.entity.StudyContext;
import com.naeil.study.studycontext.repository.StudyContextRepository;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.repository.TopicRepository;
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
    private final Clock clock;

    public AnalysisStateWriter(
            StudySessionRepository studySessionRepository,
            DocumentRepository documentRepository,
            StudyContextRepository studyContextRepository,
            TopicRepository topicRepository,
            Clock clock
    ) {
        this.studySessionRepository = studySessionRepository;
        this.documentRepository = documentRepository;
        this.studyContextRepository = studyContextRepository;
        this.topicRepository = topicRepository;
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
     */
    @Transactional
    public List<Topic> completeAnalysis(UUID sessionId, List<ValidatedTopic> validatedTopics) {
        StudySession session = studySessionRepository.findById(sessionId)
                .orElseThrow(SessionNotFoundException::new);
        LocalDateTime now = now();

        topicRepository.deleteAllByStudySessionId(sessionId);
        topicRepository.flush();

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
