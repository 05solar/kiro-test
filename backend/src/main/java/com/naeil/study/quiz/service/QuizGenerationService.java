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
import com.naeil.study.quiz.exception.QuizGenerationFailedException;
import com.naeil.study.quiz.exception.QuizGenerationInProgressException;
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

    /**
     * 지금 새 회차를 만들고 있는 Topic.
     *
     * <p>버튼을 두 번 누르거나 응답을 기다리다 새로고침하면 AI 가 두 번 불린다.
     * 그만큼 과금되고 회차도 둘로 갈린다. 진행 중이면 두 번째 요청을 거절한다.
     *
     * <p>인스턴스 안에서만 유효하다. 여러 대로 늘리면 각자 자기 것만 알기 때문에
     * 그때는 DB 나 캐시를 쓰는 잠금으로 바꿔야 한다. 지금은 단일 인스턴스 전제다.
     */
    private final java.util.Set<UUID> inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();

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

        int latestRound = quizRepository.findLatestRound(topic.getId());
        if (latestRound > 0) {
            // 이미 만든 회차가 있으면 AI 를 부르지 않는다. 새 문제를 원하면 regenerate 를 쓴다.
            return new QuizGenerationResult(
                    topic,
                    quizRepository.findAllByTopicIdAndRoundOrderByQuizOrderAsc(topic.getId(), latestRound),
                    false);
        }
        return createRound(session, topic, 1, List.of());
    }

    /**
     * 같은 학습 범위로 <b>새 회차</b>의 문제를 만든다.
     *
     * <p>기존 문제를 고치거나 지우지 않는다. 회차를 하나 올려 새로 쌓는다.
     * 지난 회차의 문제와 답안 기록이 남아 있어야 무엇을 이미 풀었는지 알 수 있고,
     * 다음 회차에서 중복을 피할 근거도 된다.
     *
     * <p>"오답 다시 풀기"와 다른 기능이다. 그쪽은 이미 낸 문제를 다시 보는 것이고,
     * 이쪽은 같은 범위에서 <b>다른 문제</b>를 만든다.
     *
     * <p><b>중복 호출을 막는다.</b> 버튼을 두 번 누르거나 새로고침해도 AI 가 두 번 불리면
     * 그만큼 과금되고 회차도 둘로 갈린다. 같은 Topic 에 대해 생성이 진행 중이면
     * 새로 부르지 않고 거절한다.
     *
     * @throws QuizGenerationInProgressException 같은 Topic 의 생성이 이미 진행 중
     */
    public QuizGenerationResult regenerate(String sessionCode, UUID topicId) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        Topic topic = findTopic(session, topicId);

        int latestRound = quizRepository.findLatestRound(topic.getId());
        if (latestRound == 0) {
            // 아직 한 번도 만들지 않았다. 새 회차가 아니라 첫 회차를 만든다.
            return createRound(session, topic, 1, List.of());
        }

        // 이전 회차에 낸 문제 문장만 가져온다. 보기·정답·해설은 중복 판단에 필요 없다.
        List<String> previousQuestions = quizRepository.findQuestionsByTopicId(topic.getId());

        if (!inFlight.add(topic.getId())) {
            throw new QuizGenerationInProgressException();
        }
        try {
            return createRound(session, topic, latestRound + 1, previousQuestions);
        } finally {
            inFlight.remove(topic.getId());
        }
    }

    /** 회차 하나를 만들어 저장한다. 첫 회차와 새 회차가 같은 경로를 지나게 한다. */
    private QuizGenerationResult createRound(
            StudySession session, Topic topic, int round, List<String> previousQuestions) {

        ensureStudyCompleted(session, topic);

        String sourceContext = extractSourceContext(session, topic);
        AiStudyContext studyContext = loadStudyContext(session);

        AiQuizGenerationRequest request = new AiQuizGenerationRequest(
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
                questionsPerTopic,
                previousQuestions);

        List<ValidatedQuizQuestion> validated = generateValidated(session, topic, request);

        // 전체 검증을 통과한 뒤에만 저장한다. saveAllAndFlush 한 번이 하나의 트랜잭션이라
        // 문제 일부만 남는 상태가 생기지 않는다.
        LocalDateTime now = LocalDateTime.now(clock);
        List<Quiz> quizzes = quizRepository.saveAllAndFlush(toQuizzes(topic, round, validated, now));

        log.info("quizzes generated: sessionId={}, topicId={}, round={}, count={}, previous={}, contextChars={}",
                session.getId(), topic.getId(), round, quizzes.size(),
                previousQuestions.size(), sourceContext.length());
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

        // 화면이 보는 것은 언제나 마지막 회차다. 지난 회차는 기록으로만 남는다.
        int latestRound = quizRepository.findLatestRound(topic.getId());
        if (latestRound == 0) {
            throw new QuizNotFoundException();
        }
        return new QuizGenerationResult(
                topic,
                quizRepository.findAllByTopicIdAndRoundOrderByQuizOrderAsc(topic.getId(), latestRound),
                false);
    }

    private Topic findTopic(StudySession session, UUID topicId) {
        return topicRepository.findByIdAndStudySessionId(topicId, session.getId())
                .orElseThrow(TopicNotFoundException::new);
    }

    /**
     * AI 를 호출하고 응답을 검증한다. 검증에 실패하면 <b>한 번만</b> 다시 요청한다.
     *
     * <p>연결 오류·일시적 서버 오류의 재시도는 클라이언트 계층이 담당하지만, "보기 3개",
     * "문항 수 부족" 같은 형식 위반은 같은 요청을 다시 보내면 대개 정상 응답이 온다
     * (생성이 확률적이기 때문이다). 두 번째도 실패하면 그대로 실패시킨다 —
     * 반복 호출은 비용만 늘고, 사용자는 다시 시도할 수 있다.
     */
    private List<ValidatedQuizQuestion> generateValidated(
            StudySession session, Topic topic, AiQuizGenerationRequest request) {
        AiQuizGenerationResult aiResult = aiQuizClient.generate(request);
        try {
            return responseValidator.validate(aiResult, questionsPerTopic);
        } catch (QuizGenerationFailedException first) {
            log.warn("quiz response invalid, retrying once: sessionId={}, topicId={}, reason={}",
                    session.getId(), topic.getId(), first.getReason());
            AiQuizGenerationResult retried = aiQuizClient.generate(request);
            return responseValidator.validate(retried, questionsPerTopic);
        }
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

    private List<Quiz> toQuizzes(
            Topic topic, int round, List<ValidatedQuizQuestion> validated, LocalDateTime now) {
        List<Quiz> quizzes = new ArrayList<>(validated.size());
        for (ValidatedQuizQuestion question : validated) {
            quizzes.add(Quiz.create(
                    topic,
                    round,
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
