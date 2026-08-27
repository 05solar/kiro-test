package com.naeil.study.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.quiz.entity.QuizDifficulty;
import com.naeil.study.quiz.entity.QuizResult;
import com.naeil.study.quiz.exception.InvalidQuizOptionException;
import com.naeil.study.quiz.exception.QuizNotFoundException;
import com.naeil.study.quiz.repository.QuizRepository;
import com.naeil.study.quiz.repository.QuizResultRepository;
import com.naeil.study.quiz.service.QuizAnswerService.AnswerResult;
import com.naeil.study.quiz.service.QuizAnswerService.TopicQuizResults;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import com.naeil.study.topic.exception.TopicNotFoundException;
import com.naeil.study.topic.repository.TopicRepository;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuizAnswerService - 채점과 집계")
class QuizAnswerServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 21, 0, 0);
    private static final UUID SESSION_ID = UUID.fromString("8f79a1b2-0000-4000-8000-000000000001");
    private static final UUID TOPIC_ID = UUID.fromString("8f79a1b2-0000-4000-8000-000000000002");
    private static final UUID QUIZ_ID = UUID.fromString("8f79a1b2-0000-4000-8000-000000000003");
    private static final String SESSION_CODE = "7K2M9QXF";

    @Mock
    private SessionService sessionService;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizResultRepository quizResultRepository;

    private QuizAnswerService service;
    private StudySession session;
    private Topic topic;
    private Quiz quiz;

    @BeforeEach
    void setUp() throws Exception {
        service = new QuizAnswerService(
                sessionService, topicRepository, quizRepository, quizResultRepository,
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));

        session = StudySession.create(SESSION_CODE, NOW.minusHours(5), 30L);
        setId(StudySession.class, session, SESSION_ID);

        topic = Topic.create(session, "CPU 스케줄링", "요약", List.of("Round Robin"),
                TopicImportance.HIGH, 40, false, false, false, false, List.of(), 1, NOW.minusHours(1));
        setId(Topic.class, topic, TOPIC_ID);

        quiz = quiz(QUIZ_ID, 1, 2);
    }

    private void setId(Class<?> type, Object target, UUID id) throws Exception {
        Field field = type.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    private Quiz quiz(UUID id, int order, int correctIndex) throws Exception {
        Quiz created = Quiz.create(topic, order, "문제 " + order, List.of("A", "B", "C", "D"),
                correctIndex, "해설", QuizDifficulty.MEDIUM, List.of(), NOW.minusMinutes(30));
        setId(Quiz.class, created, id);
        return created;
    }

    private void givenSession() {
        given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
    }

    private void givenQuiz() {
        given(quizRepository.findByIdAndTopicStudySessionId(QUIZ_ID, SESSION_ID))
                .willReturn(Optional.of(quiz));
    }

    @Nested
    @DisplayName("답안 제출")
    class Answer {

        @Test
        @DisplayName("정답을 고르면 정답으로 채점한다")
        void gradesCorrectAnswer() {
            givenSession();
            givenQuiz();
            given(quizResultRepository.findByStudySessionIdAndQuizId(SESSION_ID, QUIZ_ID))
                    .willReturn(Optional.empty());
            given(quizResultRepository.save(any(QuizResult.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            AnswerResult result = service.answer(SESSION_CODE, QUIZ_ID, 2);

            assertThat(result.result().isCorrect()).isTrue();
            assertThat(result.result().getSelectedIndex()).isEqualTo(2);
            assertThat(result.result().getAnsweredAt()).isEqualTo(NOW);
            assertThat(result.alreadyAnswered()).isFalse();
        }

        @Test
        @DisplayName("오답을 고르면 오답으로 채점한다")
        void gradesWrongAnswer() {
            givenSession();
            givenQuiz();
            given(quizResultRepository.findByStudySessionIdAndQuizId(SESSION_ID, QUIZ_ID))
                    .willReturn(Optional.empty());
            given(quizResultRepository.save(any(QuizResult.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            AnswerResult result = service.answer(SESSION_CODE, QUIZ_ID, 1);

            assertThat(result.result().isCorrect()).isFalse();
            // 응답에서 정답과 해설을 공개할 수 있도록 퀴즈를 함께 돌려준다
            assertThat(result.quiz().getCorrectIndex()).isEqualTo(2);
        }

        @Test
        @DisplayName("보기 번호가 0~3 을 벗어나면 거절한다")
        void rejectsOutOfRangeOption() {
            givenSession();
            givenQuiz();

            for (int invalid : new int[] {-1, 4, 100}) {
                assertThatThrownBy(() -> service.answer(SESSION_CODE, QUIZ_ID, invalid))
                        .isInstanceOf(InvalidQuizOptionException.class);
            }
            verify(quizResultRepository, never()).save(any());
        }

        @Test
        @DisplayName("다른 세션의 문제에는 답할 수 없다")
        void rejectsQuizOfAnotherSession() {
            givenSession();
            given(quizRepository.findByIdAndTopicStudySessionId(QUIZ_ID, SESSION_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.answer(SESSION_CODE, QUIZ_ID, 0))
                    .isInstanceOf(QuizNotFoundException.class);
        }

        @Test
        @DisplayName("이미 답한 문제는 기존 결과를 돌려주고 답을 바꾸지 않는다")
        void keepsFirstAnswer() {
            givenSession();
            givenQuiz();
            QuizResult first = QuizResult.create(session, quiz, 1, false, NOW.minusMinutes(5));
            given(quizResultRepository.findByStudySessionIdAndQuizId(SESSION_ID, QUIZ_ID))
                    .willReturn(Optional.of(first));

            AnswerResult again = service.answer(SESSION_CODE, QUIZ_ID, 2);

            assertThat(again.alreadyAnswered()).isTrue();
            assertThat(again.result().getSelectedIndex()).isEqualTo(1);
            assertThat(again.result().isCorrect()).isFalse();
            verify(quizResultRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("결과 집계")
    class Results {

        private List<Quiz> fiveQuizzes() throws Exception {
            List<Quiz> quizzes = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                quizzes.add(quiz(UUID.randomUUID(), i, 0));
            }
            return quizzes;
        }

        private void givenTopic() {
            given(topicRepository.findByIdAndStudySessionId(TOPIC_ID, SESSION_ID))
                    .willReturn(Optional.of(topic));
        }

        @Test
        @DisplayName("5문제 중 4문제를 맞히면 80%다")
        void aggregatesFullResults() throws Exception {
            givenSession();
            givenTopic();
            List<Quiz> quizzes = fiveQuizzes();
            given(quizRepository.findAllByTopicIdOrderByQuizOrderAsc(TOPIC_ID)).willReturn(quizzes);
            List<QuizResult> results = List.of(
                    QuizResult.create(session, quizzes.get(0), 0, true, NOW),
                    QuizResult.create(session, quizzes.get(1), 0, true, NOW),
                    QuizResult.create(session, quizzes.get(2), 0, true, NOW),
                    QuizResult.create(session, quizzes.get(3), 0, true, NOW),
                    QuizResult.create(session, quizzes.get(4), 1, false, NOW));
            given(quizResultRepository.findAllByStudySessionIdAndQuizTopicId(SESSION_ID, TOPIC_ID))
                    .willReturn(results);

            TopicQuizResults aggregated = service.results(SESSION_CODE, TOPIC_ID);

            assertThat(aggregated.totalQuestions()).isEqualTo(5);
            assertThat(aggregated.answeredQuestions()).isEqualTo(5);
            assertThat(aggregated.correctAnswers()).isEqualTo(4);
            assertThat(aggregated.scorePercentage()).isEqualTo(80);
            assertThat(aggregated.completed()).isTrue();
        }

        @Test
        @DisplayName("아직 다 풀지 않았으면 completed 가 아니다")
        void aggregatesPartialResults() throws Exception {
            givenSession();
            givenTopic();
            List<Quiz> quizzes = fiveQuizzes();
            given(quizRepository.findAllByTopicIdOrderByQuizOrderAsc(TOPIC_ID)).willReturn(quizzes);
            List<QuizResult> results = List.of(
                    QuizResult.create(session, quizzes.get(0), 0, true, NOW),
                    QuizResult.create(session, quizzes.get(1), 1, false, NOW),
                    QuizResult.create(session, quizzes.get(2), 0, true, NOW));
            given(quizResultRepository.findAllByStudySessionIdAndQuizTopicId(SESSION_ID, TOPIC_ID))
                    .willReturn(results);

            TopicQuizResults aggregated = service.results(SESSION_CODE, TOPIC_ID);

            assertThat(aggregated.answeredQuestions()).isEqualTo(3);
            assertThat(aggregated.correctAnswers()).isEqualTo(2);
            // 점수는 전체 문제 수 기준이다: 2 / 5 = 40%
            assertThat(aggregated.scorePercentage()).isEqualTo(40);
            assertThat(aggregated.completed()).isFalse();
        }

        @Test
        @DisplayName("퀴즈가 없는 Topic 은 집계할 수 없다")
        void rejectsTopicWithoutQuizzes() {
            givenSession();
            givenTopic();
            given(quizRepository.findAllByTopicIdOrderByQuizOrderAsc(TOPIC_ID)).willReturn(List.of());

            assertThatThrownBy(() -> service.results(SESSION_CODE, TOPIC_ID))
                    .isInstanceOf(QuizNotFoundException.class);
        }

        @Test
        @DisplayName("다른 세션의 Topic 은 집계할 수 없다")
        void rejectsTopicOfAnotherSession() {
            givenSession();
            given(topicRepository.findByIdAndStudySessionId(TOPIC_ID, SESSION_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.results(SESSION_CODE, TOPIC_ID))
                    .isInstanceOf(TopicNotFoundException.class);
        }
    }
}
