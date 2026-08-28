package com.naeil.study.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.naeil.study.curriculum.entity.Curriculum;
import com.naeil.study.curriculum.entity.SkipReason;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.exception.CurriculumNotFoundException;
import com.naeil.study.curriculum.repository.CurriculumRepository;
import com.naeil.study.curriculum.repository.StudyStepRepository;
import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.quiz.entity.QuizDifficulty;
import com.naeil.study.quiz.entity.QuizResult;
import com.naeil.study.quiz.repository.QuizRepository;
import com.naeil.study.quiz.repository.QuizResultRepository;
import com.naeil.study.review.service.SessionReviewService.SessionReview;
import com.naeil.study.review.service.SessionReviewService.StepReview;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
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
@DisplayName("SessionReviewService - 세션 전체 정리")
class SessionReviewServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 21, 0, 0);
    private static final UUID SESSION_ID = UUID.fromString("9a11b2c3-0000-4000-8000-000000000001");
    private static final UUID CURRICULUM_ID = UUID.fromString("9a11b2c3-0000-4000-8000-000000000002");
    private static final String SESSION_CODE = "7K2M9QXF";

    @Mock
    private SessionService sessionService;

    @Mock
    private CurriculumRepository curriculumRepository;

    @Mock
    private StudyStepRepository studyStepRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizResultRepository quizResultRepository;

    private SessionReviewService service;
    private StudySession session;
    private Curriculum curriculum;

    @BeforeEach
    void setUp() throws Exception {
        service = new SessionReviewService(sessionService, curriculumRepository,
                studyStepRepository, quizRepository, quizResultRepository);

        session = StudySession.create(SESSION_CODE, NOW.minusHours(5), 30L);
        setId(StudySession.class, session, SESSION_ID);

        curriculum = Curriculum.create(session, 300, 280, NOW.minusHours(4));
        setId(Curriculum.class, curriculum, CURRICULUM_ID);
    }

    private void setId(Class<?> type, Object target, UUID id) throws Exception {
        Field field = type.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    private Topic topic(String title, int order) throws Exception {
        Topic created = Topic.create(session, title, title + " 요약", List.of(title + " 핵심"),
                TopicImportance.HIGH, 40, false, false, false, false, List.of(), order,
                NOW.minusHours(3));
        setId(Topic.class, created, UUID.randomUUID());
        return created;
    }

    private StudyStep step(Topic topic, int order) throws Exception {
        StudyStep created = StudyStep.study(curriculum, topic, order, topic.getTitle(),
                40, 40, true, List.of(), NOW.minusHours(3));
        setId(StudyStep.class, created, UUID.randomUUID());
        return created;
    }

    private Quiz quiz(Topic topic, int round, int order) throws Exception {
        Quiz created = Quiz.create(topic, round, order, "문제 " + order,
                List.of("A", "B", "C", "D"), 0, "해설", QuizDifficulty.MEDIUM, List.of(),
                NOW.minusHours(2));
        setId(Quiz.class, created, UUID.randomUUID());
        return created;
    }

    private void givenSession() {
        given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
        given(curriculumRepository.findByStudySessionId(SESSION_ID))
                .willReturn(Optional.of(curriculum));
    }

    @Test
    @DisplayName("스텝마다 요약과 퀴즈 내역을 붙여 돌려준다")
    void collectsStepsWithQuizzes() throws Exception {
        givenSession();
        Topic first = topic("CPU 스케줄링", 1);
        Topic second = topic("메모리 관리", 2);
        StudyStep firstStep = step(first, 1);
        StudyStep secondStep = step(second, 2);
        given(studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(CURRICULUM_ID))
                .willReturn(List.of(firstStep, secondStep));

        Quiz q1 = quiz(first, 1, 1);
        Quiz q2 = quiz(first, 1, 2);
        Quiz q3 = quiz(second, 1, 1);
        given(quizRepository.findAllBySessionIdWithTopic(SESSION_ID))
                .willReturn(List.of(q1, q2, q3));
        given(quizResultRepository.findAllByStudySessionId(SESSION_ID)).willReturn(List.of(
                QuizResult.create(session, q1, 0, true, NOW),
                QuizResult.create(session, q2, 1, false, NOW),
                QuizResult.create(session, q3, 0, true, NOW)));

        SessionReview review = service.review(SESSION_CODE);

        assertThat(review.steps()).hasSize(2);
        assertThat(review.totalQuestions()).isEqualTo(3);
        assertThat(review.answeredQuestions()).isEqualTo(3);
        assertThat(review.correctAnswers()).isEqualTo(2);
        assertThat(review.wrongAnswers()).isEqualTo(1);
        // 푼 문제 기준이다: 2 / 3
        assertThat(review.scorePercentage()).isEqualTo(67);

        StepReview firstReview = review.steps().get(0);
        assertThat(firstReview.topic().getSummary()).isEqualTo("CPU 스케줄링 요약");
        assertThat(firstReview.items()).hasSize(2);
        assertThat(firstReview.wrongQuestions()).isEqualTo(1);
    }

    @Test
    @DisplayName("회차가 여러 개면 마지막 회차만 담는다")
    void keepsOnlyLatestRound() throws Exception {
        givenSession();
        Topic only = topic("CPU 스케줄링", 1);
        given(studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(CURRICULUM_ID))
                .willReturn(List.of(step(only, 1)));

        Quiz old = quiz(only, 1, 1);
        Quiz fresh = quiz(only, 2, 1);
        given(quizRepository.findAllBySessionIdWithTopic(SESSION_ID))
                .willReturn(List.of(old, fresh));
        given(quizResultRepository.findAllByStudySessionId(SESSION_ID))
                .willReturn(List.of(QuizResult.create(session, old, 0, true, NOW)));

        SessionReview review = service.review(SESSION_CODE);

        StepReview step = review.steps().get(0);
        assertThat(step.round()).isEqualTo(2);
        assertThat(step.items()).hasSize(1);
        // 2회차는 아직 안 풀었다. 1회차 답안이 여기 섞여 들어오면 안 된다.
        assertThat(step.answeredQuestions()).isZero();
    }

    @Test
    @DisplayName("Topic 이 없는 복습 스텝도 자리를 지킨다")
    void keepsReviewStepWithoutTopic() throws Exception {
        givenSession();
        StudyStep reviewStep = StudyStep.review(curriculum, 1, "전체 복습", 20, NOW.minusHours(3));
        setId(StudyStep.class, reviewStep, UUID.randomUUID());
        given(studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(CURRICULUM_ID))
                .willReturn(List.of(reviewStep));
        given(quizRepository.findAllBySessionIdWithTopic(SESSION_ID)).willReturn(List.of());
        given(quizResultRepository.findAllByStudySessionId(SESSION_ID)).willReturn(List.of());

        SessionReview review = service.review(SESSION_CODE);

        assertThat(review.steps()).hasSize(1);
        assertThat(review.steps().get(0).topic()).isNull();
        assertThat(review.steps().get(0).items()).isEmpty();
    }

    @Test
    @DisplayName("건너뛴 스텝이 있어도 나머지를 마쳤으면 완료다")
    void treatsSkippedStepAsSettled() throws Exception {
        givenSession();
        Topic done = topic("CPU 스케줄링", 1);
        Topic cut = topic("메모리 관리", 2);
        StudyStep doneStep = step(done, 1);
        doneStep.start(NOW.minusHours(2));
        doneStep.complete(NOW.minusHours(1));
        StudyStep cutStep = step(cut, 2);
        cutStep.skip(SkipReason.TIME_CONSTRAINT, NOW.minusHours(1));

        given(studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(CURRICULUM_ID))
                .willReturn(List.of(doneStep, cutStep));
        given(quizRepository.findAllBySessionIdWithTopic(SESSION_ID)).willReturn(List.of());
        given(quizResultRepository.findAllByStudySessionId(SESSION_ID)).willReturn(List.of());

        SessionReview review = service.review(SESSION_CODE);

        // 시간이 모자라 잘라 낸 스텝까지 끝내야 완료라면 영영 완료가 되지 않는다
        assertThat(review.completed()).isTrue();
        assertThat(review.completedSteps()).isEqualTo(1);
        assertThat(review.totalSteps()).isEqualTo(2);
    }

    @Test
    @DisplayName("아직 남은 스텝이 있으면 완료가 아니다")
    void notCompletedWhilePendingRemains() throws Exception {
        givenSession();
        Topic done = topic("CPU 스케줄링", 1);
        Topic pending = topic("메모리 관리", 2);
        StudyStep doneStep = step(done, 1);
        doneStep.start(NOW.minusHours(2));
        doneStep.complete(NOW.minusHours(1));

        given(studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(CURRICULUM_ID))
                .willReturn(List.of(doneStep, step(pending, 2)));
        given(quizRepository.findAllBySessionIdWithTopic(SESSION_ID)).willReturn(List.of());
        given(quizResultRepository.findAllByStudySessionId(SESSION_ID)).willReturn(List.of());

        SessionReview review = service.review(SESSION_CODE);

        assertThat(review.completed()).isFalse();
    }

    @Test
    @DisplayName("안 푼 문제는 정답률 분모에서 뺀다")
    void excludesUnansweredFromScore() throws Exception {
        givenSession();
        Topic only = topic("CPU 스케줄링", 1);
        given(studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(CURRICULUM_ID))
                .willReturn(List.of(step(only, 1)));

        List<Quiz> quizzes = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            quizzes.add(quiz(only, 1, i));
        }
        given(quizRepository.findAllBySessionIdWithTopic(SESSION_ID)).willReturn(quizzes);
        given(quizResultRepository.findAllByStudySessionId(SESSION_ID)).willReturn(List.of(
                QuizResult.create(session, quizzes.get(0), 0, true, NOW),
                QuizResult.create(session, quizzes.get(1), 1, false, NOW)));

        SessionReview review = service.review(SESSION_CODE);

        assertThat(review.totalQuestions()).isEqualTo(4);
        assertThat(review.answeredQuestions()).isEqualTo(2);
        // 2문제만 풀어 1개를 맞혔으므로 50%다. 안 푼 2문제는 세지 않는다.
        assertThat(review.scorePercentage()).isEqualTo(50);
    }

    @Test
    @DisplayName("학습 계획이 없으면 정리할 것이 없다")
    void rejectsSessionWithoutCurriculum() {
        given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
        given(curriculumRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.review(SESSION_CODE))
                .isInstanceOf(CurriculumNotFoundException.class);
    }
}
