package com.naeil.study.wronganswer.service;

import com.naeil.study.analysis.client.dto.AiStudyContext;
import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentStatus;
import com.naeil.study.document.repository.DocumentRepository;
import com.naeil.study.quiz.context.QuizContextExtractor;
import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.quiz.entity.QuizResult;
import com.naeil.study.quiz.exception.QuizNotFoundException;
import com.naeil.study.quiz.repository.QuizRepository;
import com.naeil.study.quiz.repository.QuizResultRepository;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.studycontext.repository.StudyContextRepository;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.wronganswer.client.AiWrongAnswerSummaryClient;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerItem;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerSummaryRequest;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerSummaryResult;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerTopic;
import com.naeil.study.wronganswer.entity.WrongAnswerSummary;
import com.naeil.study.wronganswer.exception.QuizNotCompletedException;
import com.naeil.study.wronganswer.exception.WrongAnswerSummaryNotFoundException;
import com.naeil.study.wronganswer.repository.WrongAnswerSummaryRepository;
import com.naeil.study.wronganswer.validation.AiWrongAnswerSummaryValidator;
import com.naeil.study.wronganswer.validation.AiWrongAnswerSummaryValidator.TopicIdentity;
import com.naeil.study.wronganswer.validation.AiWrongAnswerSummaryValidator.ValidatedSummary;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Quiz 오답 기반 맞춤형 복습 요약의 생성 / 조회.
 *
 * <p><b>읽기만 하고 원본은 바꾸지 않는다.</b> Quiz / QuizResult / Topic / StudyContext /
 * Curriculum / 남은 학습 시간을 수정하지 않는다. 이 기능의 산출물은 {@link WrongAnswerSummary}
 * 하나뿐이다.
 *
 * <p><b>캐시.</b> 저장된 요약의 {@code sourceLatestAnsweredAt} 이 현재 답안들의 최신 시각과
 * 같으면 AI를 다시 부르지 않는다. 새 답안이 생겼을 때만 재생성하고, 재생성 실패 시
 * 기존 요약은 지우지 않는다.
 *
 * <p>클래스에 트랜잭션을 걸지 않는다. AI 호출 동안 DB 커넥션을 잡아 두지 않기 위해서다
 * (퀴즈 생성 서비스와 같은 이유). 저장은 {@code save} 한 번이 하나의 트랜잭션이다.
 */
@Service
public class WrongAnswerSummaryService {

    private static final Logger log = LoggerFactory.getLogger(WrongAnswerSummaryService.class);

    private final SessionService sessionService;
    private final QuizRepository quizRepository;
    private final QuizResultRepository quizResultRepository;
    private final DocumentRepository documentRepository;
    private final StudyContextRepository studyContextRepository;
    private final WrongAnswerSummaryRepository summaryRepository;
    private final QuizContextExtractor contextExtractor;
    private final AiWrongAnswerSummaryClient aiClient;
    private final AiWrongAnswerSummaryValidator validator;
    private final Clock clock;
    private final int maxContextCharactersPerTopic;

    public WrongAnswerSummaryService(
            SessionService sessionService,
            QuizRepository quizRepository,
            QuizResultRepository quizResultRepository,
            DocumentRepository documentRepository,
            StudyContextRepository studyContextRepository,
            WrongAnswerSummaryRepository summaryRepository,
            QuizContextExtractor contextExtractor,
            AiWrongAnswerSummaryClient aiClient,
            AiWrongAnswerSummaryValidator validator,
            Clock clock,
            @Value("${wrong-answer-summary.max-context-characters-per-topic:8000}") int maxContextCharactersPerTopic
    ) {
        this.sessionService = sessionService;
        this.quizRepository = quizRepository;
        this.quizResultRepository = quizResultRepository;
        this.documentRepository = documentRepository;
        this.studyContextRepository = studyContextRepository;
        this.summaryRepository = summaryRepository;
        this.contextExtractor = contextExtractor;
        this.aiClient = aiClient;
        this.validator = validator;
        this.clock = clock;
        this.maxContextCharactersPerTopic = maxContextCharactersPerTopic;
    }

    /**
     * 요약 결과.
     *
     * @param hasWrongAnswers 오답이 하나라도 있으면 true. false 면 {@code summary} 는 null
     * @param summary         저장된 요약. 오답이 없으면 null
     * @param generated       이번 요청에서 AI 로 새로 생성(또는 재생성)했으면 true
     */
    public record SummaryOutcome(
            boolean hasWrongAnswers,
            WrongAnswerSummary summary,
            boolean generated
    ) {
    }

    /**
     * 세션 전체 오답으로 복습 요약을 만든다.
     *
     * <p>처리 순서
     * <pre>
     * 세션 → 퀴즈 존재 → 전부 답변했는지 → 오답 추출 → (오답 없으면 AI 없이 반환)
     *   → 캐시 확인 → Topic 별 그룹화 + 관련 구간 추출 → AI 호출 → 검증 → 저장(교체)
     * </pre>
     *
     * @throws QuizNotFoundException     세션에 생성된 퀴즈가 하나도 없음
     * @throws QuizNotCompletedException 아직 답하지 않은 퀴즈가 있음
     * @throws com.naeil.study.wronganswer.exception.WrongAnswerSummaryGenerationFailedException
     *         AI 실패 / 응답 검증 실패. 기존 요약은 지워지지 않는다
     */
    public SummaryOutcome generate(String sessionCode) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        UUID sessionId = session.getId();

        List<Quiz> quizzes = quizRepository.findAllBySessionIdWithTopic(sessionId);
        if (quizzes.isEmpty()) {
            throw new QuizNotFoundException();
        }
        List<QuizResult> results = quizResultRepository.findAllByStudySessionId(sessionId);
        if (results.size() < quizzes.size()) {
            throw new QuizNotCompletedException();
        }

        List<QuizResult> wrong = results.stream().filter(result -> !result.isCorrect()).toList();
        if (wrong.isEmpty()) {
            // 모든 문제를 맞혔다. 오류가 아니고, AI 를 부를 일도 없다.
            return new SummaryOutcome(false, null, false);
        }

        LocalDateTime latestAnsweredAt = results.stream()
                .map(QuizResult::getAnsweredAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();

        WrongAnswerSummary existing = summaryRepository.findByStudySessionId(sessionId).orElse(null);
        if (existing != null && existing.isUpToDate(latestAnsweredAt)) {
            return new SummaryOutcome(true, existing, false);
        }

        RequestBundle bundle = buildRequest(session, quizzes, wrong);
        AiWrongAnswerSummaryResult aiResult = aiClient.generate(bundle.request());
        ValidatedSummary validated = validator.validate(aiResult, bundle.identities());

        LocalDateTime now = LocalDateTime.now(clock);
        WrongAnswerSummary saved;
        if (existing != null) {
            existing.replaceWith(wrong.size(), validated.overallSummary(),
                    validated.topicReviews(), latestAnsweredAt, now);
            saved = summaryRepository.save(existing);
        } else {
            saved = summaryRepository.save(WrongAnswerSummary.create(
                    session, wrong.size(), validated.overallSummary(),
                    validated.topicReviews(), latestAnsweredAt, now));
        }

        log.info("wrong-answer summary generated: sessionId={}, wrongAnswers={}, topics={}, refreshed={}",
                sessionId, wrong.size(), validated.topicReviews().size(), existing != null);
        return new SummaryOutcome(true, saved, true);
    }

    /**
     * 저장된 복습 요약을 조회한다.
     *
     * @throws WrongAnswerSummaryNotFoundException 아직 생성하지 않은 경우
     */
    public SummaryOutcome find(String sessionCode) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        WrongAnswerSummary summary = summaryRepository.findByStudySessionId(session.getId())
                .orElseThrow(WrongAnswerSummaryNotFoundException::new);
        return new SummaryOutcome(true, summary, false);
    }

    private record RequestBundle(
            AiWrongAnswerSummaryRequest request,
            Map<String, TopicIdentity> identities
    ) {
    }

    /**
     * 오답을 Topic 별로 묶어 AI 요청을 만든다.
     *
     * <p>Topic 순서는 원래 학습 순서({@code topicOrder})를 따른다. 참조값(TOPIC_n)은
     * 서버가 만들고, 응답 검증에서 실제 UUID 로 되돌린다.
     */
    private RequestBundle buildRequest(StudySession session, List<Quiz> quizzes, List<QuizResult> wrong) {
        Map<UUID, Quiz> quizById = new LinkedHashMap<>();
        quizzes.forEach(quiz -> quizById.put(quiz.getId(), quiz));

        // quizzes 가 topicOrder 순이므로 삽입 순서를 유지하면 Topic 도 학습 순서를 따른다
        Map<UUID, Topic> topics = new LinkedHashMap<>();
        Map<UUID, List<AiWrongAnswerItem>> itemsByTopic = new LinkedHashMap<>();
        for (Quiz quiz : quizzes) {
            Topic topic = quiz.getTopic();
            topics.putIfAbsent(topic.getId(), topic);
            itemsByTopic.computeIfAbsent(topic.getId(), id -> new ArrayList<>());
        }
        for (QuizResult result : wrong) {
            Quiz quiz = quizById.get(result.getQuiz().getId());
            // 인덱스가 아니라 보기의 실제 문자열을 전달한다. 의미가 더 안정적으로 전해진다.
            itemsByTopic.get(quiz.getTopic().getId()).add(new AiWrongAnswerItem(
                    quiz.getQuestion(),
                    quiz.getOptions(),
                    quiz.getOptions().get(result.getSelectedIndex()),
                    quiz.getOptions().get(quiz.getCorrectIndex()),
                    quiz.getExplanation()));
        }

        List<Document> parsedDocuments = documentRepository
                .findAllByStudySessionIdAndStatusOrderByCreatedAtAsc(session.getId(), DocumentStatus.PARSED);

        Map<String, TopicIdentity> identities = new LinkedHashMap<>();
        List<AiWrongAnswerTopic> aiTopics = new ArrayList<>();
        int referenceIndex = 1;
        for (Topic topic : topics.values()) {
            List<AiWrongAnswerItem> items = itemsByTopic.get(topic.getId());
            if (items.isEmpty()) {
                // 오답이 없는 Topic 은 요청에 포함하지 않는다
                continue;
            }
            String reference = "TOPIC_" + referenceIndex++;
            identities.put(reference, new TopicIdentity(topic.getId(), topic.getTitle()));
            aiTopics.add(new AiWrongAnswerTopic(
                    reference,
                    topic.getTitle(),
                    topic.getImportance().name(),
                    topic.isProfessorEmphasisMatched(),
                    topic.isPastExamMatched(),
                    topic.isWeakAreaMatched(),
                    topic.isMustStudyMatched(),
                    List.copyOf(items),
                    extractContext(topic, parsedDocuments)));
        }

        AiStudyContext studyContext = studyContextRepository.findByStudySessionId(session.getId())
                .map(context -> new AiStudyContext(
                        context.getProfessorEmphasis(),
                        context.getPastExamInfo(),
                        context.getWeakAreas(),
                        context.getMustStudyAreas()))
                .orElse(AiStudyContext.empty());

        return new RequestBundle(
                new AiWrongAnswerSummaryRequest(session.getSubject(), studyContext, aiTopics),
                identities);
    }

    /**
     * Topic 출처 문서에서 관련 구간을 추출한다. 퀴즈 생성과 같은 방식이다.
     *
     * <p>출처 문서가 지워져 구간이 비어도 요약 전체를 실패시키지 않는다. 그 Topic 은
     * 퀴즈 해설(생성 당시 이미 자료에 근거해 검증된 값)을 근거로 요약된다.
     */
    private String extractContext(Topic topic, List<Document> parsedDocuments) {
        List<UUID> sourceIds = topic.getSourceDocumentIds();
        List<Document> sources = sourceIds.isEmpty()
                ? parsedDocuments
                : parsedDocuments.stream()
                        .filter(document -> sourceIds.contains(document.getId())).toList();
        if (sources.isEmpty()) {
            sources = parsedDocuments;
        }
        return contextExtractor.extract(topic, sources, maxContextCharactersPerTopic);
    }
}
