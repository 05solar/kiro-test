package com.naeil.study.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naeil.study.curriculum.entity.Curriculum;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.repository.StudyStepRepository;
import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.entity.DocumentStatus;
import com.naeil.study.document.repository.DocumentRepository;
import com.naeil.study.quiz.client.FakeAiQuizClient;
import com.naeil.study.quiz.client.dto.AiQuizGenerationRequest;
import com.naeil.study.quiz.client.dto.AiQuizGenerationResult;
import com.naeil.study.quiz.context.QuizContextExtractor;
import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.quiz.entity.QuizDifficulty;
import com.naeil.study.quiz.exception.NoQuizSourceContextException;
import com.naeil.study.quiz.exception.QuizGenerationFailedException;
import com.naeil.study.quiz.exception.QuizNotFoundException;
import com.naeil.study.quiz.exception.TopicStudyNotCompletedException;
import com.naeil.study.quiz.repository.QuizRepository;
import com.naeil.study.quiz.service.QuizGenerationService.QuizGenerationResult;
import com.naeil.study.quiz.validation.AiQuizResponseValidator;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.storage.StoredFile;
import com.naeil.study.studycontext.entity.StudyContext;
import com.naeil.study.studycontext.repository.StudyContextRepository;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import com.naeil.study.topic.exception.TopicNotFoundException;
import com.naeil.study.topic.repository.TopicRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuizGenerationService - 퀴즈 생성")
class QuizGenerationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 20, 0, 0);
    private static final UUID SESSION_ID = UUID.fromString("7f79a1b2-0000-4000-8000-000000000001");
    private static final UUID TOPIC_ID = UUID.fromString("7f79a1b2-0000-4000-8000-000000000002");
    private static final UUID DOCUMENT_ID = UUID.fromString("7f79a1b2-0000-4000-8000-000000000003");
    private static final String SESSION_CODE = "7K2M9QXF";
    private static final int QUESTIONS_PER_TOPIC = 5;

    @Mock
    private SessionService sessionService;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private StudyStepRepository studyStepRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private StudyContextRepository studyContextRepository;

    @Mock
    private QuizRepository quizRepository;

    private FakeAiQuizClient aiQuizClient;
    private QuizGenerationService service;
    private StudySession session;
    private Topic topic;
    private Curriculum curriculum;

    @BeforeEach
    void setUp() throws Exception {
        aiQuizClient = new FakeAiQuizClient();
        service = new QuizGenerationService(
                sessionService,
                topicRepository,
                studyStepRepository,
                documentRepository,
                studyContextRepository,
                quizRepository,
                new QuizContextExtractor(20_000),
                aiQuizClient,
                new AiQuizResponseValidator(),
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE),
                QUESTIONS_PER_TOPIC);

        session = StudySession.create(SESSION_CODE, NOW.minusHours(5), 30L);
        setId(StudySession.class, session, SESSION_ID);
        session.updateExamInfo("운영체제", NOW.plusHours(4), 180, 180, NOW.minusHours(4));

        topic = Topic.create(session, "CPU 스케줄링", "요약", List.of("Round Robin"),
                TopicImportance.HIGH, 40, true, false, true, false,
                List.of(DOCUMENT_ID), 1, NOW.minusHours(1));
        setId(Topic.class, topic, TOPIC_ID);

        curriculum = Curriculum.create(session, 180, 180, NOW.minusHours(1));
    }

    private void setId(Class<?> type, Object target, UUID id) throws Exception {
        Field field = type.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    private void givenSessionAndTopic() {
        given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
        given(topicRepository.findByIdAndStudySessionId(TOPIC_ID, SESSION_ID))
                .willReturn(Optional.of(topic));
    }

    private StudyStep completedStep() {
        StudyStep step = StudyStep.study(curriculum, topic, 1, "CPU 스케줄링",
                40, 40, false, List.of(), NOW.minusHours(1));
        step.start(NOW.minusMinutes(50));
        step.complete(NOW.minusMinutes(10));
        return step;
    }

    private Document parsedDocument(String text) throws Exception {
        Document document = Document.create(session, "강의.txt",
                new StoredFile("stored.txt", "sessions/x/stored.txt"),
                DocumentFileType.TXT, text.length(), NOW.minusHours(2));
        setId(Document.class, document, DOCUMENT_ID);
        document.markParsed(text, NOW.minusHours(2));
        return document;
    }

    private void givenGenerationReady() throws Exception {
        givenSessionAndTopic();
        given(quizRepository.findLatestRound(TOPIC_ID)).willReturn(0);
        given(studyStepRepository.findFirstByCurriculumStudySessionIdAndTopicId(SESSION_ID, TOPIC_ID))
                .willReturn(Optional.of(completedStep()));
        given(documentRepository.findAllByStudySessionIdAndStatusOrderByCreatedAtAsc(
                SESSION_ID, DocumentStatus.PARSED))
                .willReturn(List.of(parsedDocument("CPU 스케줄링은 준비 큐를 다룬다. Round Robin 은 선점형이다.")));
        given(studyContextRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("학습을 완료한 Topic 의 퀴즈를 생성해 일괄 저장한다")
    void generatesAndSavesQuizzes() throws Exception {
        givenGenerationReady();
        given(quizRepository.saveAllAndFlush(anyList()))
                .willAnswer(invocation -> invocation.getArgument(0));

        QuizGenerationResult result = service.generate(SESSION_CODE, TOPIC_ID);

        assertThat(result.created()).isTrue();
        assertThat(result.quizzes()).hasSize(5);
        assertThat(result.quizzes().get(0).getQuizOrder()).isEqualTo(1);
        assertThat(result.quizzes().get(0).getOptions()).hasSize(4);
        assertThat(result.quizzes().get(0).getDifficulty()).isEqualTo(QuizDifficulty.EASY);
        // 출처는 Topic 의 출처 문서를 그대로 복사한다. AI가 문서 ID를 만들지 않는다
        assertThat(result.quizzes().get(0).getSourceDocumentIds()).containsExactly(DOCUMENT_ID);
        assertThat(aiQuizClient.callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AI 요청에 강의자료 추출 구간과 Topic 정보가 들어간다")
    void sendsSourceContextAndTopicInfo() throws Exception {
        givenGenerationReady();
        given(quizRepository.saveAllAndFlush(anyList()))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.generate(SESSION_CODE, TOPIC_ID);

        AiQuizGenerationRequest request = aiQuizClient.requests().get(0);
        assertThat(request.subject()).isEqualTo("운영체제");
        assertThat(request.topicTitle()).isEqualTo("CPU 스케줄링");
        assertThat(request.keyPoints()).containsExactly("Round Robin");
        assertThat(request.professorEmphasisMatched()).isTrue();
        assertThat(request.weakAreaMatched()).isTrue();
        assertThat(request.sourceContext()).contains("준비 큐");
        assertThat(request.questionCount()).isEqualTo(QUESTIONS_PER_TOPIC);
    }

    @Test
    @DisplayName("학습 맥락이 있으면 AI 요청에 함께 전달한다")
    void sendsStudyContext() throws Exception {
        givenGenerationReady();
        given(studyContextRepository.findByStudySessionId(SESSION_ID))
                .willReturn(Optional.of(StudyContext.create(
                        session, "교착상태 4조건 강조", "기출: RR 계산", "가상 메모리", "스케줄링 전부", NOW)));
        given(quizRepository.saveAllAndFlush(anyList()))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.generate(SESSION_CODE, TOPIC_ID);

        AiQuizGenerationRequest request = aiQuizClient.requests().get(0);
        assertThat(request.studyContext().professorEmphasis()).isEqualTo("교착상태 4조건 강조");
        assertThat(request.studyContext().pastExamInfo()).isEqualTo("기출: RR 계산");
        assertThat(request.studyContext().weakAreas()).isEqualTo("가상 메모리");
        assertThat(request.studyContext().mustStudyAreas()).isEqualTo("스케줄링 전부");
    }

    @Test
    @DisplayName("이미 퀴즈가 있으면 AI를 부르지 않고 기존 것을 돌려준다")
    void returnsExistingWithoutAiCall() {
        givenSessionAndTopic();
        Quiz existing = Quiz.create(topic, 1, 1, "기존 문제", List.of("A", "B", "C", "D"),
                0, "해설", QuizDifficulty.MEDIUM, List.of(DOCUMENT_ID), NOW.minusMinutes(30));
        given(quizRepository.findLatestRound(TOPIC_ID)).willReturn(1);
        given(quizRepository.findAllByTopicIdAndRoundOrderByQuizOrderAsc(TOPIC_ID, 1))
                .willReturn(List.of(existing));

        QuizGenerationResult result = service.generate(SESSION_CODE, TOPIC_ID);

        assertThat(result.created()).isFalse();
        assertThat(result.quizzes()).containsExactly(existing);
        assertThat(aiQuizClient.callCount()).isZero();
        verify(quizRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("다른 세션의 Topic 은 찾을 수 없다")
    void rejectsTopicOfAnotherSession() {
        given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
        given(topicRepository.findByIdAndStudySessionId(TOPIC_ID, SESSION_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(SESSION_CODE, TOPIC_ID))
                .isInstanceOf(TopicNotFoundException.class);
        assertThat(aiQuizClient.callCount()).isZero();
    }

    @Test
    @DisplayName("학습 단계를 완료하지 않았으면 생성할 수 없다")
    void rejectsWhenStudyNotCompleted() {
        givenSessionAndTopic();
        given(quizRepository.findLatestRound(TOPIC_ID)).willReturn(0);
        StudyStep inProgress = StudyStep.study(curriculum, topic, 1, "CPU 스케줄링",
                40, 40, false, List.of(), NOW.minusHours(1));
        inProgress.start(NOW.minusMinutes(30));
        given(studyStepRepository.findFirstByCurriculumStudySessionIdAndTopicId(SESSION_ID, TOPIC_ID))
                .willReturn(Optional.of(inProgress));

        assertThatThrownBy(() -> service.generate(SESSION_CODE, TOPIC_ID))
                .isInstanceOf(TopicStudyNotCompletedException.class);
        assertThat(aiQuizClient.callCount()).isZero();
    }

    @Test
    @DisplayName("계획에 들어가지 못한 Topic 도 학습 미완료로 본다")
    void rejectsTopicNotInCurriculum() {
        givenSessionAndTopic();
        given(quizRepository.findLatestRound(TOPIC_ID)).willReturn(0);
        given(studyStepRepository.findFirstByCurriculumStudySessionIdAndTopicId(SESSION_ID, TOPIC_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(SESSION_CODE, TOPIC_ID))
                .isInstanceOf(TopicStudyNotCompletedException.class);
    }

    @Test
    @DisplayName("근거로 쓸 강의자료 텍스트가 없으면 생성할 수 없다")
    void rejectsWithoutSourceContext() {
        givenSessionAndTopic();
        given(quizRepository.findLatestRound(TOPIC_ID)).willReturn(0);
        given(studyStepRepository.findFirstByCurriculumStudySessionIdAndTopicId(SESSION_ID, TOPIC_ID))
                .willReturn(Optional.of(completedStep()));
        given(documentRepository.findAllByStudySessionIdAndStatusOrderByCreatedAtAsc(
                SESSION_ID, DocumentStatus.PARSED)).willReturn(List.of());

        assertThatThrownBy(() -> service.generate(SESSION_CODE, TOPIC_ID))
                .isInstanceOf(NoQuizSourceContextException.class);
        assertThat(aiQuizClient.callCount()).isZero();
    }

    @Test
    @DisplayName("AI 응답이 검증에 실패하면 아무것도 저장하지 않는다")
    void savesNothingWhenValidationFails() throws Exception {
        givenGenerationReady();
        aiQuizClient.respondWith(() -> new AiQuizGenerationResult(List.of(
                FakeAiQuizClient.question("문제 하나뿐", 0, "EASY"))));

        assertThatThrownBy(() -> service.generate(SESSION_CODE, TOPIC_ID))
                .isInstanceOf(QuizGenerationFailedException.class);
        verify(quizRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("조회는 아직 퀴즈가 없으면 404다")
    void findRejectsWhenNoQuizzes() {
        givenSessionAndTopic();
        given(quizRepository.findLatestRound(TOPIC_ID)).willReturn(0);

        assertThatThrownBy(() -> service.find(SESSION_CODE, TOPIC_ID))
                .isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    @DisplayName("저장되는 문제 수를 검증 결과와 함께 확인한다")
    void savesValidatedQuestions() throws Exception {
        givenGenerationReady();
        given(quizRepository.saveAllAndFlush(anyList()))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.generate(SESSION_CODE, TOPIC_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Quiz>> captor = ArgumentCaptor.forClass(List.class);
        verify(quizRepository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).hasSize(5);
        assertThat(captor.getValue().get(4).getQuizOrder()).isEqualTo(5);
    }

    /**
     * "새로운 퀴즈 만들기" 검증.
     *
     * <p>여기서도 실제 AI 를 부르지 않는다. 이 기능은 사용자가 여러 번 누르게 되는
     * 종류라 특히 자동 테스트에서 부르면 안 된다.
     */
    @Nested
    @DisplayName("새로운 퀴즈 만들기")
    class Regenerate {

        @SuppressWarnings("unchecked")
        private ArgumentCaptor<List<Quiz>> captureSaved() {
            ArgumentCaptor<List<Quiz>> captor = ArgumentCaptor.forClass(List.class);
            verify(quizRepository).saveAllAndFlush(captor.capture());
            return captor;
        }

        private void givenExistingRound(int round, List<String> previousQuestions) throws Exception {
            givenGenerationReady();
            given(quizRepository.findLatestRound(TOPIC_ID)).willReturn(round);
            given(quizRepository.findQuestionsByTopicId(TOPIC_ID)).willReturn(previousQuestions);
            given(quizRepository.saveAllAndFlush(anyList()))
                    .willAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        @DisplayName("기존 문제를 지우지 않고 다음 회차로 쌓는다")
        void createsNextRound() throws Exception {
            givenExistingRound(1, List.of("1회차 문제 A", "1회차 문제 B"));

            QuizGenerationResult result = service.regenerate(SESSION_CODE, TOPIC_ID);

            assertThat(result.created()).isTrue();
            // 2회차로 저장된다. 1회차 문제와 답안 기록은 그대로 남는다.
            assertThat(captureSaved().getValue())
                    .allSatisfy(quiz -> assertThat(quiz.getRound()).isEqualTo(2));
        }

        @Test
        @DisplayName("이전 문제 문장을 AI 요청에 함께 보낸다")
        void sendsPreviousQuestions() throws Exception {
            givenExistingRound(1, List.of("이미 낸 문제"));

            service.regenerate(SESSION_CODE, TOPIC_ID);

            AiQuizGenerationRequest sent = aiQuizClient.requests().getLast();
            assertThat(sent.previousQuestions()).containsExactly("이미 낸 문제");
            assertThat(sent.hasPrevious()).isTrue();
        }

        @Test
        @DisplayName("보기·정답·해설은 보내지 않는다 — 중복 판단에 필요 없다")
        void sendsOnlyQuestionText() throws Exception {
            givenExistingRound(1, List.of("이미 낸 문제"));

            service.regenerate(SESSION_CODE, TOPIC_ID);

            // 리포지터리에서 문제 문장만 가져온다. 회차가 쌓일수록 프롬프트가 길어지는 것을 막는다.
            verify(quizRepository).findQuestionsByTopicId(TOPIC_ID);
            assertThat(aiQuizClient.requests().getLast().previousQuestions())
                    .allSatisfy(question -> assertThat(question).doesNotContain("보기", "해설"));
        }

        @Test
        @DisplayName("한 번도 만들지 않았으면 첫 회차를 만든다")
        void createsFirstRoundWhenNone() throws Exception {
            givenGenerationReady();
            given(quizRepository.saveAllAndFlush(anyList()))
                    .willAnswer(invocation -> invocation.getArgument(0));

            service.regenerate(SESSION_CODE, TOPIC_ID);

            // 이전 문제가 없으니 중복 방지 지시도 넣지 않는다.
            assertThat(aiQuizClient.requests().getLast().hasPrevious()).isFalse();
            assertThat(captureSaved().getValue())
                    .allSatisfy(quiz -> assertThat(quiz.getRound()).isEqualTo(1));
        }
    }
}
