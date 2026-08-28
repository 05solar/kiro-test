package com.naeil.study.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naeil.study.curriculum.entity.Curriculum;
import com.naeil.study.curriculum.entity.CurriculumStatus;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.entity.StudyStepStatus;
import com.naeil.study.curriculum.entity.StudyStepType;
import com.naeil.study.curriculum.exception.CurriculumGenerationFailedException;
import com.naeil.study.curriculum.exception.CurriculumNotFoundException;
import com.naeil.study.curriculum.exception.NoStudyTimeAvailableException;
import com.naeil.study.curriculum.exception.SessionNotReadyException;
import com.naeil.study.curriculum.exception.TopicsRequiredException;
import com.naeil.study.curriculum.planner.CurriculumPlanner;
import com.naeil.study.curriculum.repository.CurriculumRepository;
import com.naeil.study.curriculum.repository.StudyStepRepository;
import com.naeil.study.curriculum.service.CurriculumService.CurriculumResult;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
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
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurriculumService - 최초 학습 계획")
class CurriculumServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 18, 0, 0);
    private static final UUID SESSION_ID = UUID.fromString("6f79a1b2-0000-4000-8000-000000000001");
    private static final String SESSION_CODE = "7K2M9QXF";

    @Mock
    private CurriculumRepository curriculumRepository;

    @Mock
    private StudyStepRepository studyStepRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private SessionService sessionService;

    @Captor
    private ArgumentCaptor<List<StudyStep>> stepsCaptor;

    private CurriculumService curriculumService;
    private StudySession session;
    private int nextTopicOrder;

    @BeforeEach
    void setUp() throws Exception {
        Clock fixedClock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        curriculumService = new CurriculumService(
                curriculumRepository, studyStepRepository, topicRepository, sessionService,
                new CurriculumPlanner(5, 10, 45), fixedClock);
        session = StudySession.create(SESSION_CODE, NOW.minusHours(2), 30L);
        setId(StudySession.class, session, SESSION_ID);
        nextTopicOrder = 1;
    }

    private void setId(Class<?> type, Object target, UUID id) throws Exception {
        Field field = type.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    /** 분석까지 끝나 계획을 만들 수 있는 세션으로 만든다. */
    private void givenReadySession(int remainingMinutes, LocalDateTime examAt) {
        session.updateExamInfo("운영체제", null, examAt, remainingMinutes, remainingMinutes, NOW.minusHours(1));
        session.startAnalyzing(com.naeil.study.session.entity.StudySourceType.USER_MATERIAL, NOW.minusMinutes(30));
        session.markReady(NOW.minusMinutes(10));
        given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
    }

    private Topic topic(String title, TopicImportance importance, int estimated) throws Exception {
        return topic(title, importance, estimated, false, false, false, false);
    }

    private Topic topic(
            String title, TopicImportance importance, int estimated,
            boolean professor, boolean pastExam, boolean weak, boolean mustStudy) throws Exception {
        Topic topic = Topic.create(session, title, "요약", List.of("개념"), importance, estimated,
                professor, pastExam, weak, mustStudy, List.of(), nextTopicOrder++, NOW);
        setId(Topic.class, topic, UUID.randomUUID());
        return topic;
    }

    private void givenTopics(Topic... topics) {
        given(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(SESSION_ID))
                .willReturn(List.of(topics));
    }

    private void givenNoExistingCurriculum() {
        given(curriculumRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());
    }

    private void givenSaveSucceeds() {
        given(curriculumRepository.save(any(Curriculum.class))).willAnswer(i -> i.getArgument(0));
        given(studyStepRepository.saveAllAndFlush(anyList())).willAnswer(i -> i.getArgument(0));
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("남은 시간 안에 들어가는 계획을 만든다")
        void createsCurriculumWithinRemainingMinutes() throws Exception {
            givenReadySession(180, NOW.plusDays(1));
            givenNoExistingCurriculum();
            givenTopics(
                    topic("프로세스", TopicImportance.VERY_HIGH, 50),
                    topic("CPU 스케줄링", TopicImportance.HIGH, 60),
                    topic("파일 시스템", TopicImportance.LOW, 40));
            givenSaveSucceeds();

            CurriculumResult result = curriculumService.create(SESSION_CODE);

            assertThat(result.created()).isTrue();
            assertThat(result.curriculum().getInitialRemainingMinutes()).isEqualTo(180);
            assertThat(result.curriculum().getStatus()).isEqualTo(CurriculumStatus.CREATED);
            assertThat(result.curriculum().getTotalAllocatedMinutes()).isLessThanOrEqualTo(180);
            assertThat(result.steps()).isNotEmpty();
            assertThat(result.steps()).allSatisfy(
                    step -> assertThat(step.getStatus()).isEqualTo(StudyStepStatus.PENDING));
        }

        @Test
        @DisplayName("단계에 계획 시점 값과 배정 시간을 함께 남긴다")
        void recordsBothEstimatedAndAllocatedMinutes() throws Exception {
            givenReadySession(60, NOW.plusDays(1));
            givenNoExistingCurriculum();
            givenTopics(topic("프로세스", TopicImportance.VERY_HIGH, 100));
            givenSaveSucceeds();

            curriculumService.create(SESSION_CODE);

            verify(studyStepRepository).saveAllAndFlush(stepsCaptor.capture());
            StudyStep step = stepsCaptor.getValue().get(0);
            assertThat(step.getOriginalEstimatedMinutes()).isEqualTo(100);
            assertThat(step.getAllocatedMinutes()).isLessThanOrEqualTo(60);
            assertThat(step.getActualStudyMinutes()).isNull();
            assertThat(step.getStartedAt()).isNull();
            assertThat(step.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("계획을 만들어도 세션 상태와 현재 단계는 그대로다")
        void doesNotStartStudying() throws Exception {
            givenReadySession(180, NOW.plusDays(1));
            givenNoExistingCurriculum();
            givenTopics(topic("프로세스", TopicImportance.HIGH, 40));
            givenSaveSucceeds();

            curriculumService.create(SESSION_CODE);

            assertThat(session.isReady()).isTrue();
            assertThat(session.getCurrentStepOrder()).isNull();
        }

        @Test
        @DisplayName("이미 계획이 있으면 다시 만들지 않고 기존 것을 돌려준다")
        void returnsExistingCurriculum() throws Exception {
            given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
            Curriculum existing = Curriculum.create(session, 180, 170, NOW.minusHours(1));
            setId(Curriculum.class, existing, UUID.randomUUID());
            given(curriculumRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.of(existing));
            given(studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(existing.getId()))
                    .willReturn(List.of());

            CurriculumResult result = curriculumService.create(SESSION_CODE);

            assertThat(result.created()).isFalse();
            assertThat(result.curriculum()).isSameAs(existing);
            verify(curriculumRepository, never()).save(any(Curriculum.class));
            verify(topicRepository, never()).findAllByStudySessionIdOrderByTopicOrderAsc(any());
        }

        @Test
        @DisplayName("시험까지 남은 시간이 더 짧으면 그 시간에 맞춰 계획한다")
        void clampsToMinutesUntilExam() throws Exception {
            // 저장된 남은 시간은 300분이지만 시험까지 180분뿐이다
            givenReadySession(300, NOW.plusMinutes(180));
            givenNoExistingCurriculum();
            givenTopics(
                    topic("A", TopicImportance.VERY_HIGH, 120),
                    topic("B", TopicImportance.HIGH, 120),
                    topic("C", TopicImportance.MEDIUM, 120));
            givenSaveSucceeds();

            CurriculumResult result = curriculumService.create(SESSION_CODE);

            assertThat(result.curriculum().getInitialRemainingMinutes()).isEqualTo(180);
            assertThat(result.curriculum().getTotalAllocatedMinutes()).isLessThanOrEqualTo(180);
            // 세션의 남은 시간도 실제 시간에 맞춰 낮춘다
            assertThat(session.getRemainingStudyMinutes()).isEqualTo(180);
        }

        @Test
        @DisplayName("시험까지 시간이 더 많으면 저장된 남은 시간을 그대로 쓴다")
        void keepsRemainingMinutesWhenExamIsFarAway() throws Exception {
            givenReadySession(120, NOW.plusDays(3));
            givenNoExistingCurriculum();
            givenTopics(topic("A", TopicImportance.HIGH, 60));
            givenSaveSucceeds();

            CurriculumResult result = curriculumService.create(SESSION_CODE);

            assertThat(result.curriculum().getInitialRemainingMinutes()).isEqualTo(120);
            assertThat(session.getRemainingStudyMinutes()).isEqualTo(120);
        }

        @Test
        @DisplayName("반드시 학습할 주제는 중요도가 낮아도 계획에 남는다")
        void keepsMustStudyTopic() throws Exception {
            givenReadySession(60, NOW.plusDays(1));
            givenNoExistingCurriculum();
            Topic mustStudy = topic("교착상태", TopicImportance.LOW, 30, false, false, false, true);
            givenTopics(topic("CPU 스케줄링", TopicImportance.VERY_HIGH, 60), mustStudy);
            givenSaveSucceeds();

            curriculumService.create(SESSION_CODE);

            verify(studyStepRepository).saveAllAndFlush(stepsCaptor.capture());
            assertThat(stepsCaptor.getValue())
                    .anySatisfy(step -> {
                        assertThat(step.getTitle()).isEqualTo("교착상태");
                        assertThat(step.isMandatory()).isTrue();
                    });
        }

        @Test
        @DisplayName("분석이 끝나지 않은 세션은 계획을 만들 수 없다")
        void failsWhenSessionIsNotReady() {
            session.startAnalyzing(com.naeil.study.session.entity.StudySourceType.USER_MATERIAL, NOW.minusMinutes(5));
            given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
            givenNoExistingCurriculum();

            assertThatThrownBy(() -> curriculumService.create(SESSION_CODE))
                    .isInstanceOf(SessionNotReadyException.class);

            verify(curriculumRepository, never()).save(any(Curriculum.class));
        }

        @Test
        @DisplayName("분석된 주제가 없으면 계획을 만들 수 없다")
        void failsWithoutTopics() {
            givenReadySession(180, NOW.plusDays(1));
            givenNoExistingCurriculum();
            given(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(SESSION_ID))
                    .willReturn(List.of());

            assertThatThrownBy(() -> curriculumService.create(SESSION_CODE))
                    .isInstanceOf(TopicsRequiredException.class);
        }

        @Test
        @DisplayName("남은 학습 시간이 0이면 계획을 만들 수 없다")
        void failsWithoutStudyTime() throws Exception {
            givenReadySession(0, NOW.plusDays(1));
            givenNoExistingCurriculum();
            givenTopics(topic("A", TopicImportance.VERY_HIGH, 30));

            assertThatThrownBy(() -> curriculumService.create(SESSION_CODE))
                    .isInstanceOf(NoStudyTimeAvailableException.class);
        }

        @Test
        @DisplayName("시험 시각이 이미 지났으면 계획을 만들 수 없다")
        void failsWhenExamHasPassed() throws Exception {
            givenReadySession(180, NOW.minusMinutes(10));
            givenNoExistingCurriculum();
            givenTopics(topic("A", TopicImportance.VERY_HIGH, 30));

            assertThatThrownBy(() -> curriculumService.create(SESSION_CODE))
                    .isInstanceOf(NoStudyTimeAvailableException.class);
        }

        @Test
        @DisplayName("남은 시간이 최소 학습시간보다 적으면 계획 생성에 실패한다")
        void failsWhenRemainingIsBelowMinimum() throws Exception {
            givenReadySession(3, NOW.plusDays(1));
            givenNoExistingCurriculum();
            givenTopics(topic("A", TopicImportance.VERY_HIGH, 30));

            assertThatThrownBy(() -> curriculumService.create(SESSION_CODE))
                    .isInstanceOf(CurriculumGenerationFailedException.class);
        }

        @Test
        @DisplayName("존재하지 않는 세션이면 404가 발생한다")
        void failsWhenSessionNotFound() {
            given(sessionService.getSessionAndTouch("ZZZZZZZZ")).willThrow(new SessionNotFoundException());

            assertThatThrownBy(() -> curriculumService.create("ZZZZZZZZ"))
                    .isInstanceOf(SessionNotFoundException.class);
        }

        @Test
        @DisplayName("복습 단계는 Topic 없이 만들어진다")
        void createsReviewStepWithoutTopic() throws Exception {
            givenReadySession(200, NOW.plusDays(1));
            givenNoExistingCurriculum();
            givenTopics(topic("A", TopicImportance.HIGH, 40), topic("B", TopicImportance.HIGH, 40));
            givenSaveSucceeds();

            curriculumService.create(SESSION_CODE);

            verify(studyStepRepository).saveAllAndFlush(stepsCaptor.capture());
            List<StudyStep> steps = stepsCaptor.getValue();
            StudyStep last = steps.get(steps.size() - 1);
            assertThat(last.getType()).isEqualTo(StudyStepType.REVIEW);
            assertThat(last.getTopic()).isNull();
            assertThat(last.isMandatory()).isFalse();
        }

        @Test
        @DisplayName("계획 생성은 세션 접근으로 기록된다")
        void refreshesSessionAccessTime() throws Exception {
            givenReadySession(180, NOW.plusDays(1));
            givenNoExistingCurriculum();
            givenTopics(topic("A", TopicImportance.HIGH, 40));
            givenSaveSucceeds();

            curriculumService.create(SESSION_CODE);

            verify(sessionService).getSessionAndTouch(SESSION_CODE);
        }
    }

    @Nested
    @DisplayName("조회")
    class Find {

        @Test
        @DisplayName("저장된 계획을 단계와 함께 돌려준다")
        void returnsCurriculumWithSteps() throws Exception {
            given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
            Curriculum curriculum = Curriculum.create(session, 180, 170, NOW);
            setId(Curriculum.class, curriculum, UUID.randomUUID());
            given(curriculumRepository.findByStudySessionId(SESSION_ID))
                    .willReturn(Optional.of(curriculum));
            StudyStep step = StudyStep.review(curriculum, 1, "복습", 20, NOW);
            given(studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(curriculum.getId()))
                    .willReturn(List.of(step));

            CurriculumResult result = curriculumService.find(SESSION_CODE);

            assertThat(result.curriculum()).isSameAs(curriculum);
            assertThat(result.steps()).containsExactly(step);
        }

        @Test
        @DisplayName("아직 계획을 만들지 않았으면 404가 발생한다")
        void failsWhenCurriculumIsAbsent() {
            given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
            given(curriculumRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> curriculumService.find(SESSION_CODE))
                    .isInstanceOf(CurriculumNotFoundException.class);
        }
    }
}
