package com.naeil.study.wronganswer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.entity.DocumentStatus;
import com.naeil.study.document.repository.DocumentRepository;
import com.naeil.study.quiz.context.QuizContextExtractor;
import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.quiz.entity.QuizDifficulty;
import com.naeil.study.quiz.entity.QuizResult;
import com.naeil.study.quiz.exception.QuizNotFoundException;
import com.naeil.study.quiz.repository.QuizRepository;
import com.naeil.study.quiz.repository.QuizResultRepository;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.storage.StoredFile;
import com.naeil.study.studycontext.entity.StudyContext;
import com.naeil.study.studycontext.repository.StudyContextRepository;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import com.naeil.study.wronganswer.client.FakeAiWrongAnswerSummaryClient;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerSummaryRequest;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerTopic;
import com.naeil.study.wronganswer.entity.ReviewPriority;
import com.naeil.study.wronganswer.entity.TopicReviewSnapshot;
import com.naeil.study.wronganswer.entity.WrongAnswerSummary;
import com.naeil.study.wronganswer.exception.QuizNotCompletedException;
import com.naeil.study.wronganswer.exception.WrongAnswerSummaryGenerationFailedException;
import com.naeil.study.wronganswer.exception.WrongAnswerSummaryNotFoundException;
import com.naeil.study.wronganswer.repository.WrongAnswerSummaryRepository;
import com.naeil.study.wronganswer.service.WrongAnswerSummaryService.SummaryOutcome;
import com.naeil.study.wronganswer.validation.AiWrongAnswerSummaryValidator;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WrongAnswerSummaryService - 오답 복습 요약")
class WrongAnswerSummaryServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 22, 0, 0);
    private static final UUID SESSION_ID = UUID.fromString("aa79a1b2-0000-4000-8000-000000000001");
    private static final UUID DOC_A_ID = UUID.fromString("aa79a1b2-0000-4000-8000-00000000000a");
    private static final UUID DOC_B_ID = UUID.fromString("aa79a1b2-0000-4000-8000-00000000000b");
    private static final String SESSION_CODE = "7K2M9QXF";

    @Mock
    private SessionService sessionService;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizResultRepository quizResultRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private StudyContextRepository studyContextRepository;

    @Mock
    private WrongAnswerSummaryRepository summaryRepository;

    private FakeAiWrongAnswerSummaryClient aiClient;
    private WrongAnswerSummaryService service;
    private StudySession session;
    private Topic topic1;
    private Topic topic2;
    private List<Quiz> quizzes;

    @BeforeEach
    void setUp() throws Exception {
        aiClient = new FakeAiWrongAnswerSummaryClient();
        service = new WrongAnswerSummaryService(
                sessionService,
                quizRepository,
                quizResultRepository,
                documentRepository,
                studyContextRepository,
                summaryRepository,
                new QuizContextExtractor(20_000),
                aiClient,
                new AiWrongAnswerSummaryValidator(),
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE),
                8_000);

        session = StudySession.create(SESSION_CODE, NOW.minusHours(6), 30L);
        setId(StudySession.class, session, SESSION_ID);
        session.updateExamInfo("운영체제", NOW.plusHours(3), 300, 300, NOW.minusHours(5));

        topic1 = topic("CPU 스케줄링", List.of(DOC_A_ID), 1);
        topic2 = topic("가상 메모리", List.of(DOC_A_ID), 2);

        // topic1 문제 3개, topic2 문제 2개
        quizzes = new ArrayList<>();
        quizzes.add(quiz(topic1, 1, 0));
        quizzes.add(quiz(topic1, 2, 1));
        quizzes.add(quiz(topic1, 3, 2));
        quizzes.add(quiz(topic2, 1, 3));
        quizzes.add(quiz(topic2, 2, 0));
    }

    private void setId(Class<?> type, Object target, UUID id) throws Exception {
        Field field = type.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    private Topic topic(String title, List<UUID> sourceDocumentIds, int order) throws Exception {
        Topic topic = Topic.create(session, title, "요약", List.of(title + " 핵심"),
                TopicImportance.HIGH, 40, false, false, false, false,
                sourceDocumentIds, order, NOW.minusHours(4));
        setId(Topic.class, topic, UUID.randomUUID());
        return topic;
    }

    private Quiz quiz(Topic topic, int order, int correctIndex) throws Exception {
        Quiz quiz = Quiz.create(topic, order, topic.getTitle() + " 문제 " + order,
                List.of("보기A", "보기B", "보기C", "보기D"), correctIndex, "해설",
                QuizDifficulty.MEDIUM, topic.getSourceDocumentIds(), NOW.minusHours(1));
        setId(Quiz.class, quiz, UUID.randomUUID());
        return quiz;
    }

    private QuizResult result(Quiz quiz, boolean correct, LocalDateTime answeredAt) {
        int selected = correct ? quiz.getCorrectIndex() : (quiz.getCorrectIndex() + 1) % 4;
        return QuizResult.create(session, quiz, selected, correct, answeredAt);
    }

    private Document parsedDocument(UUID id, String text) throws Exception {
        Document document = Document.create(session, "강의.txt",
                new StoredFile(id + ".txt", "sessions/x/" + id + ".txt"),
                DocumentFileType.TXT, text.length(), NOW.minusHours(5));
        setId(Document.class, document, id);
        document.markParsed(text, NOW.minusHours(5));
        return document;
    }

    private void givenSession() {
        given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
    }

    private void givenQuizzes() {
        given(quizRepository.findAllBySessionIdWithTopic(SESSION_ID)).willReturn(quizzes);
    }

    private void givenResults(List<QuizResult> results) {
        given(quizResultRepository.findAllByStudySessionId(SESSION_ID)).willReturn(results);
    }

    private void givenDocuments() throws Exception {
        given(documentRepository.findAllByStudySessionIdAndStatusOrderByCreatedAtAsc(
                SESSION_ID, DocumentStatus.PARSED))
                .willReturn(List.of(
                        parsedDocument(DOC_A_ID, "CPU 스케줄링 자료 A 내용. 가상 메모리 설명도 있다."),
                        parsedDocument(DOC_B_ID, "출처가 아닌 자료 B 의 내용이다.")));
    }

    private void givenNoStudyContext() {
        given(studyContextRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());
    }

    /** 5문제 전부 답변, topic1 의 1번과 topic2 의 1번이 오답. */
    private List<QuizResult> twoWrongResults() {
        return List.of(
                result(quizzes.get(0), false, NOW.minusMinutes(50)),
                result(quizzes.get(1), true, NOW.minusMinutes(45)),
                result(quizzes.get(2), true, NOW.minusMinutes(40)),
                result(quizzes.get(3), false, NOW.minusMinutes(35)),
                result(quizzes.get(4), true, NOW.minusMinutes(30)));
    }

    @Test
    @DisplayName("오답만 Topic 별로 묶어 AI에 보내고 요약을 저장한다")
    void generatesSummaryFromWrongAnswersOnly() throws Exception {
        givenSession();
        givenQuizzes();
        givenResults(twoWrongResults());
        givenDocuments();
        givenNoStudyContext();
        given(summaryRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());
        given(summaryRepository.save(any(WrongAnswerSummary.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SummaryOutcome outcome = service.generate(SESSION_CODE);

        assertThat(outcome.hasWrongAnswers()).isTrue();
        assertThat(outcome.generated()).isTrue();
        assertThat(outcome.summary().getWrongAnswerCount()).isEqualTo(2);
        assertThat(outcome.summary().getSourceLatestAnsweredAt()).isEqualTo(NOW.minusMinutes(30));
        assertThat(outcome.summary().getTopicReviews()).hasSize(2);
        assertThat(outcome.summary().getTopicReviews().get(0).topicTitle()).isEqualTo("CPU 스케줄링");
        assertThat(outcome.summary().getTopicReviews().get(0).priority()).isEqualTo(ReviewPriority.HIGH);

        // AI 요청에는 오답이 있는 Topic 두 개, 각 오답 1건만 담긴다. 정답 문제는 없다
        AiWrongAnswerSummaryRequest request = aiClient.requests().get(0);
        assertThat(request.topics()).hasSize(2);
        assertThat(request.topics().get(0).wrongAnswers()).hasSize(1);
        assertThat(request.topics().get(1).wrongAnswers()).hasSize(1);
        assertThat(aiClient.callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("사용자 답과 정답을 인덱스가 아니라 보기 문자열로 전달한다")
    void sendsAnswersAsOptionTexts() throws Exception {
        givenSession();
        givenQuizzes();
        givenResults(twoWrongResults());
        givenDocuments();
        givenNoStudyContext();
        given(summaryRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());
        given(summaryRepository.save(any(WrongAnswerSummary.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.generate(SESSION_CODE);

        // quizzes.get(0): correctIndex 0 → 오답 선택은 1
        var wrongItem = aiClient.requests().get(0).topics().get(0).wrongAnswers().get(0);
        assertThat(wrongItem.userAnswer()).isEqualTo("보기B");
        assertThat(wrongItem.correctAnswer()).isEqualTo("보기A");
        assertThat(wrongItem.question()).isEqualTo("CPU 스케줄링 문제 1");
        assertThat(wrongItem.explanation()).isEqualTo("해설");
    }

    @Test
    @DisplayName("출처 문서의 내용만 Grounding Context 로 쓴다")
    void usesSourceDocumentsForContext() throws Exception {
        givenSession();
        givenQuizzes();
        givenResults(twoWrongResults());
        givenDocuments();
        givenNoStudyContext();
        given(summaryRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());
        given(summaryRepository.save(any(WrongAnswerSummary.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.generate(SESSION_CODE);

        AiWrongAnswerTopic first = aiClient.requests().get(0).topics().get(0);
        assertThat(first.sourceContext()).contains("자료 A");
        assertThat(first.sourceContext()).doesNotContain("자료 B");
    }

    @Test
    @DisplayName("학습 맥락이 있으면 AI 요청에 함께 전달한다")
    void sendsStudyContext() throws Exception {
        givenSession();
        givenQuizzes();
        givenResults(twoWrongResults());
        givenDocuments();
        given(studyContextRepository.findByStudySessionId(SESSION_ID))
                .willReturn(Optional.of(StudyContext.create(
                        session, "교착상태 강조", "기출 정보", "취약 영역", "필수 범위", NOW)));
        given(summaryRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());
        given(summaryRepository.save(any(WrongAnswerSummary.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.generate(SESSION_CODE);

        var context = aiClient.requests().get(0).studyContext();
        assertThat(context.professorEmphasis()).isEqualTo("교착상태 강조");
        assertThat(context.pastExamInfo()).isEqualTo("기출 정보");
        assertThat(context.weakAreas()).isEqualTo("취약 영역");
        assertThat(context.mustStudyAreas()).isEqualTo("필수 범위");
    }

    @Test
    @DisplayName("전부 맞혔으면 AI를 부르지 않고 오답 없음으로 응답한다")
    void skipsAiWhenNoWrongAnswers() {
        givenSession();
        givenQuizzes();
        givenResults(List.of(
                result(quizzes.get(0), true, NOW.minusMinutes(50)),
                result(quizzes.get(1), true, NOW.minusMinutes(45)),
                result(quizzes.get(2), true, NOW.minusMinutes(40)),
                result(quizzes.get(3), true, NOW.minusMinutes(35)),
                result(quizzes.get(4), true, NOW.minusMinutes(30))));

        SummaryOutcome outcome = service.generate(SESSION_CODE);

        assertThat(outcome.hasWrongAnswers()).isFalse();
        assertThat(outcome.summary()).isNull();
        assertThat(aiClient.callCount()).isZero();
        verify(summaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("아직 다 풀지 않았으면 요약을 만들 수 없다")
    void rejectsWhenQuizNotCompleted() {
        givenSession();
        givenQuizzes();
        givenResults(List.of(
                result(quizzes.get(0), false, NOW.minusMinutes(50)),
                result(quizzes.get(1), true, NOW.minusMinutes(45)),
                result(quizzes.get(2), true, NOW.minusMinutes(40)),
                result(quizzes.get(3), true, NOW.minusMinutes(35))));

        assertThatThrownBy(() -> service.generate(SESSION_CODE))
                .isInstanceOf(QuizNotCompletedException.class);
        assertThat(aiClient.callCount()).isZero();
    }

    @Test
    @DisplayName("퀴즈가 하나도 없으면 요약할 대상이 없다")
    void rejectsWithoutQuizzes() {
        givenSession();
        given(quizRepository.findAllBySessionIdWithTopic(SESSION_ID)).willReturn(List.of());

        assertThatThrownBy(() -> service.generate(SESSION_CODE))
                .isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    @DisplayName("답안이 바뀌지 않았으면 기존 요약을 그대로 돌려준다")
    void reusesUpToDateSummary() {
        givenSession();
        givenQuizzes();
        givenResults(twoWrongResults());
        WrongAnswerSummary existing = WrongAnswerSummary.create(session, 2, "기존 총평",
                List.of(new TopicReviewSnapshot(topic1.getId(), "CPU 스케줄링",
                        List.of("개념"), "설명", List.of("포인트"), ReviewPriority.HIGH)),
                NOW.minusMinutes(30), NOW.minusMinutes(20));
        given(summaryRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.of(existing));

        SummaryOutcome outcome = service.generate(SESSION_CODE);

        assertThat(outcome.generated()).isFalse();
        assertThat(outcome.summary()).isSameAs(existing);
        assertThat(aiClient.callCount()).isZero();
        verify(summaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("새 답안이 생겼으면 다시 생성해 기존 요약을 교체한다")
    void regeneratesWhenNewResultsExist() throws Exception {
        givenSession();
        givenQuizzes();
        givenResults(twoWrongResults());   // 최신 answeredAt = NOW-30분
        givenDocuments();
        givenNoStudyContext();
        WrongAnswerSummary existing = WrongAnswerSummary.create(session, 1, "낡은 총평",
                List.of(new TopicReviewSnapshot(topic1.getId(), "CPU 스케줄링",
                        List.of("개념"), "설명", List.of("포인트"), ReviewPriority.MEDIUM)),
                NOW.minusMinutes(90), NOW.minusMinutes(80));
        given(summaryRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.of(existing));
        given(summaryRepository.save(any(WrongAnswerSummary.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SummaryOutcome outcome = service.generate(SESSION_CODE);

        assertThat(outcome.generated()).isTrue();
        assertThat(outcome.summary()).isSameAs(existing);
        assertThat(existing.getWrongAnswerCount()).isEqualTo(2);
        assertThat(existing.getOverallSummary()).isNotEqualTo("낡은 총평");
        assertThat(existing.getSourceLatestAnsweredAt()).isEqualTo(NOW.minusMinutes(30));
        assertThat(existing.getGeneratedAt()).isEqualTo(NOW);
        assertThat(aiClient.callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("재생성 중 AI가 실패해도 기존 요약을 지우지 않는다")
    void keepsExistingSummaryWhenAiFails() throws Exception {
        givenSession();
        givenQuizzes();
        givenResults(twoWrongResults());
        givenDocuments();
        givenNoStudyContext();
        WrongAnswerSummary existing = WrongAnswerSummary.create(session, 1, "낡은 총평",
                List.of(new TopicReviewSnapshot(topic1.getId(), "CPU 스케줄링",
                        List.of("개념"), "설명", List.of("포인트"), ReviewPriority.MEDIUM)),
                NOW.minusMinutes(90), NOW.minusMinutes(80));
        given(summaryRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.of(existing));
        aiClient.failWith(new WrongAnswerSummaryGenerationFailedException("boom"));

        assertThatThrownBy(() -> service.generate(SESSION_CODE))
                .isInstanceOf(WrongAnswerSummaryGenerationFailedException.class);

        // 기존 요약은 손대지 않았다
        assertThat(existing.getOverallSummary()).isEqualTo("낡은 총평");
        assertThat(existing.getWrongAnswerCount()).isEqualTo(1);
        verify(summaryRepository, never()).save(any());
        verify(summaryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("저장된 요약이 없으면 조회는 404다")
    void findRejectsWhenNoSummary() {
        givenSession();
        given(summaryRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.find(SESSION_CODE))
                .isInstanceOf(WrongAnswerSummaryNotFoundException.class);
    }
}
