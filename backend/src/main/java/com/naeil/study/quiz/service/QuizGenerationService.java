package com.naeil.study.quiz.service;

import com.naeil.study.analysis.client.dto.AiStudyContext;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.repository.StudyStepRepository;
import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentStatus;
import com.naeil.study.document.repository.DocumentRepository;
import com.naeil.study.quiz.client.AiQuizClient;
import com.naeil.study.quiz.client.dto.AiQuizGenerationRequest;
import com.naeil.study.quiz.client.dto.AiQuizGenerationResult;
import com.naeil.study.quiz.context.QuizContextExtractor;
import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.quiz.exception.NoQuizSourceContextException;
import com.naeil.study.quiz.exception.QuizNotFoundException;
import com.naeil.study.quiz.exception.TopicStudyNotCompletedException;
import com.naeil.study.quiz.repository.QuizRepository;
import com.naeil.study.quiz.validation.AiQuizResponseValidator;
import com.naeil.study.quiz.validation.ValidatedQuizQuestion;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.studycontext.repository.StudyContextRepository;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.exception.TopicNotFoundException;
import com.naeil.study.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Topic 하나의 퀴즈 생성 / 조회.
 *
 * <p><b>멱등이다.</b> 이미 생성된 퀴즈가 있으면 AI를 다시 부르지 않고 기존 것을 돌려준다.
 * 같은 자료로 문제를 다시 만들 이유가 없고, 호출 비용만 든다. 재생성 API는 이번 단계에서
 * 만들지 않는다.
 *
 * <p><b>클래스에 트랜잭션을 걸지 않는다.</b> AI 호출이 수십 초 걸릴 수 있는데 그동안
 * DB 커넥션을 잡아 두지 않기 위해서다(분석 서비스와 같은 이유). 검증 조회는 각 리포지터리
 * 호출의 짧은 트랜잭션으로 충분하고, 저장은 {@code saveAllAndFlush} 한 번이 하나의
 * 트랜잭션이라 "문제 일부만 저장된 상태"가 생기지 않는다.
 */
@Service
public class QuizGenerationService {

    private static final Logger log = LoggerFactory.getLogger(QuizGenerationService.class);

    private final SessionService sessionService;
    private final TopicRepository topicRepository;
    private final StudyStepRepository studyStepRepository;
    private final DocumentRepository documentRepository;
    private final StudyContextRepository studyContextRepository;
    private final QuizRepository quizRepository;
    private final QuizContextExtractor contextExtractor;
    private final AiQuizClient aiQuizClient;
    private final AiQuizResponseValidator responseValidator;
    private final Clock clock;
    private final int questionsPerTopic;

    public QuizGenerationService(
            SessionService sessionService,
            TopicRepository topicRepository,
            StudyStepRepository studyStepRepository,
            DocumentRepository documentRepository,
            StudyContextRepository studyContextRepository,
            QuizRepository quizRepository,
            QuizContextExtractor contextExtractor,
            AiQuizClient aiQuizClient,
            AiQuizResponseValidator responseValidator,
            Clock clock,
            @Value("${quiz.questions-per-topic:5}") int questionsPerTopic
    ) {
        this.sessionService = sessionService;
        this.topicRepository = topicRepository;
        this.studyStepRepository = studyStepRepository;
        this.documentRepository = documentRepository;
        this.studyContextRepository = studyContextRepository;
        this.quizRepository = quizRepository;
        this.contextExtractor = contextExtractor;
        this.aiQuizClient = aiQuizClient;
        this.responseValidator = responseValidator;
        this.clock = clock;
        this.questionsPerTopic = questionsPerTopic;
    }

    /**
     * 생성 결과.
     *
     * @param created 이번 요청으로 새로 만들었으면 true, 기존 퀴즈를 돌려줬으면 false
     */
    public record QuizGenerationResult(Topic topic, List<Quiz> quizzes, boolean created) {
    }

    /**
     * Topic 의 퀴즈를 생성한다. 이미 있으면 기존 것을 돌려준다.
     *
     * <p>처리 순서
     * <pre>
     * 세션 → Topic 소유 → (기존 퀴즈 있으면 반환) → 학습 완료 확인
     *   → 출처 문서 조회 → 관련 구간 추출 → AI 호출 → 전체 검증 → 일괄 저장
     * </pre>
     *
     * @throws TopicNotFoundException          없거나 다른 세션의 Topic
     * @throws TopicStudyNotCompletedException 학습 단계를 완료하지 않음
     * @throws NoQuizSourceContextException    근거로 쓸 강의자료 텍스트가 없음
     * @throws com.naeil.study.quiz.exception.QuizGenerationFailedException AI 실패 / 응답 검증 실패
     */
    public QuizGenerationResult generate(String sessionCode, UUID topicId) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        Topic topic = findTopic(session, topicId);

        List<Quiz> existing = quizRepository.findAllByTopicIdOrderByQuizOrderAsc(topic.getId());
        if (!existing.isEmpty()) {
            return new QuizGenerationResult(topic, existing, false);
        }

        ensureStudyCompleted(session, topic);

        String sourceContext = extractSourceContext(session, topic);
        AiStudyContext studyContext = loadStudyContext(session);

        AiQuizGenerationResult aiResult = aiQuizClient.generate(new AiQuizGenerationRequest(
                session.getSubject(),
                topic.getTitle(),
                topic.getSummary(),
                topic.getKeyPoints(),
                topic.isProfessorEmphasisMatched(),
                topic.isPastExamMatched(),
                topic.isWeakAreaMatched(),
                topic.isMustStudyMatched(),
                studyContext,
                sourceContext,
                questionsPerTopic));

        List<ValidatedQuizQuestion> validated = responseValidator.validate(aiResult, questionsPerTopic);

        // 전체 검증을 통과한 뒤에만 저장한다. saveAllAndFlush 한 번이 하나의 트랜잭션이라
        // 문제 일부만 남는 상태가 생기지 않는다.
        LocalDateTime now = LocalDateTime.now(clock);
        List<Quiz> quizzes = quizRepository.saveAllAndFlush(toQuizzes(topic, validated, now));

        log.info("quizzes generated: sessionId={}, topicId={}, count={}, contextChars={}",
                session.getId(), topic.getId(), quizzes.size(), sourceContext.length());
        return new QuizGenerationResult(topic, quizzes, true);
    }

    /**
     * Topic 의 퀴즈를 조회한다.
     *
     * @throws TopicNotFoundException 없거나 다른 세션의 Topic
     * @throws QuizNotFoundException  아직 퀴즈를 생성하지 않은 경우
     */
    public QuizGenerationResult find(String sessionCode, UUID topicId) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        Topic topic = findTopic(session, topicId);

        List<Quiz> quizzes = quizRepository.findAllByTopicIdOrderByQuizOrderAsc(topic.getId());
        if (quizzes.isEmpty()) {
            throw new QuizNotFoundException();
        }
        return new QuizGenerationResult(topic, quizzes, false);
    }

    private Topic findTopic(StudySession session, UUID topicId) {
        return topicRepository.findByIdAndStudySessionId(topicId, session.getId())
                .orElseThrow(TopicNotFoundException::new);
    }

    /**
     * 이 Topic 의 학습 단계가 완료되었는지 확인한다.
     *
     * <p>퀴즈는 학습을 마친 뒤의 점검 도구다. 계획에 들어가지 못했거나(시간 부족으로 미선택),
     * SKIPPED 되었거나, 아직 진행 전·진행 중이면 모두 완료가 아니다.
     * {@code REVIEW} 단계는 Topic 이 없으므로 이 검사의 대상이 아예 되지 않는다.
     */
    private void ensureStudyCompleted(StudySession session, Topic topic) {
        StudyStep step = studyStepRepository
                .findFirstByCurriculumStudySessionIdAndTopicId(session.getId(), topic.getId())
                .orElseThrow(TopicStudyNotCompletedException::new);
        if (!step.isCompleted()) {
            throw new TopicStudyNotCompletedException();
        }
    }

    /**
     * Topic 출처 문서에서 관련 구간을 추출한다.
     *
     * <p>출처 문서만 쓴다. 세션의 모든 자료를 매번 AI에 보내지 않는다.
     * 출처 기록이 비어 있으면(참조값이 검증에서 버려진 경우) 세션의 파싱된 문서 전체로
     * 넓혀서라도 근거를 찾는다.
     */
    private String extractSourceContext(StudySession session, Topic topic) {
        List<Document> parsed = documentRepository
                .findAllByStudySessionIdAndStatusOrderByCreatedAtAsc(session.getId(), DocumentStatus.PARSED);

        List<UUID> sourceIds = topic.getSourceDocumentIds();
        List<Document> sources = sourceIds.isEmpty()
                ? parsed
                : parsed.stream().filter(document -> sourceIds.contains(document.getId())).toList();
        if (sources.isEmpty()) {
            // 출처 문서가 모두 지워졌으면 남은 파싱 문서로라도 시도한다
            sources = parsed;
        }

        String context = contextExtractor.extract(topic, sources);
        if (context.isBlank()) {
            throw new NoQuizSourceContextException();
        }
        return context;
    }

    private AiStudyContext loadStudyContext(StudySession session) {
        return studyContextRepository.findByStudySessionId(session.getId())
                .map(context -> new AiStudyContext(
                        context.getProfessorEmphasis(),
                        context.getPastExamInfo(),
                        context.getWeakAreas(),
                        context.getMustStudyAreas()))
                .orElse(AiStudyContext.empty());
    }

    private List<Quiz> toQuizzes(Topic topic, List<ValidatedQuizQuestion> validated, LocalDateTime now) {
        List<Quiz> quizzes = new ArrayList<>(validated.size());
        for (ValidatedQuizQuestion question : validated) {
            quizzes.add(Quiz.create(
                    topic,
                    question.order(),
                    question.question(),
                    question.options(),
                    question.correctIndex(),
                    question.explanation(),
                    question.difficulty(),
                    topic.getSourceDocumentIds(),
                    now));
        }
        return quizzes;
    }
}
