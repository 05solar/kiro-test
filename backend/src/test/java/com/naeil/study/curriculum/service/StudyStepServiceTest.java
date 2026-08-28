package com.naeil.study.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.naeil.study.curriculum.entity.Curriculum;
import com.naeil.study.curriculum.entity.CurriculumStatus;
import com.naeil.study.curriculum.entity.SkipReason;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.entity.StudyStepStatus;
import com.naeil.study.curriculum.exception.AnotherStepInProgressException;
import com.naeil.study.curriculum.exception.CurriculumNotFoundException;
import com.naeil.study.curriculum.exception.ExamAlreadyStartedException;
import com.naeil.study.curriculum.exception.InvalidStudyStepOrderException;
import com.naeil.study.curriculum.exception.StudyStepAlreadyCompletedException;
import com.naeil.study.curriculum.exception.StudyStepNotFoundException;
import com.naeil.study.curriculum.exception.StudyStepNotStartedException;
import com.naeil.study.curriculum.planner.CurriculumPlanner;
import com.naeil.study.curriculum.repository.CurriculumRepository;
import com.naeil.study.curriculum.repository.StudyStepRepository;
import com.naeil.study.curriculum.service.StudyStepService.CompletionResult;
import com.naeil.study.session.entity.SessionStatus;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudyStepService - 학습 진행")
class StudyStepServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 27, 19, 0, 0);
    private static final UUID SESSION_ID = UUID.fromString("6f79a1b2-0000-4000-8000-000000000001");
    private static final UUID CURRICULUM_ID = UUID.fromString("6f79a1b2-0000-4000-8000-000000000002");
    private static final String SESSION_CODE = "7K2M9QXF";

    @Mock
    private CurriculumRepository curriculumRepository;

    @Mock
    private StudyStepRepository studyStepRepository;

    @Mock
    private SessionService sessionService;

    private MovableClock clock;
    private StudyStepService studyStepService;
    private StudySession session;
    private Curriculum curriculum;

    /**
     * 시각을 앞으로 옮길 수 있는 시계.
     *
     * <p>실제 학습시간은 시작과 완료 사이의 시간이다. 고정 시계로는 항상 0분이 나와
     * 계산을 검증할 수 없다.
     */
    private static final class MovableClock extends Clock {

        private Instant instant;

        private MovableClock(LocalDateTime at) {
            this.instant = at.atZone(ZONE).toInstant();
        }

        void moveTo(LocalDateTime at) {
            this.instant = at.atZone(ZONE).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        clock = new MovableClock(START);
        CurriculumReallocationService reallocationService = new CurriculumReallocationService(
                new StudyTimeCalculator(), new CurriculumPlanner(5, 10, 45));
        studyStepService = new StudyStepService(
                curriculumRepository, studyStepRepository, sessionService, reallocationService, clock);

        session = StudySession.create(SESSION_CODE, START.minusHours(3), 30L);
        setId(StudySession.class, session, SESSION_ID);
        session.updateExamInfo("운영체제", null, START.plusHours(4), 180, 180, START.minusHours(2));
        session.startAnalyzing(com.naeil.study.session.entity.StudySourceType.USER_MATERIAL, START.minusMinutes(40));
        session.markReady(START.minusMinutes(20));

        curriculum = Curriculum.create(session, 180, 180, START.minusMinutes(10));
        setId(Curriculum.class, curriculum, CURRICULUM_ID);
    }

    private void setId(Class<?> type, Object target, UUID id) throws Exception {
        Field field = type.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    private StudyStep step(int order, String title, int allocatedMinutes) throws Exception {
        StudyStep step = StudyStep.study(curriculum, null, order, title,
                allocatedMinutes, allocatedMinutes, false, List.of(), START.minusMinutes(10));
        setId(StudyStep.class, step, UUID.randomUUID());
        return step;
    }

    private void givenSession() {
        given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
    }

    private void givenCurriculum() {
        given(curriculumRepository.findByStudySessionId(SESSION_ID))
                .willReturn(Optional.of(curriculum));
    }

    private void givenStep(StudyStep step) {
        given(studyStepRepository.findByIdAndCurriculumId(step.getId(), CURRICULUM_ID))
                .willReturn(Optional.of(step));
    }

    private void givenFirst(StudyStepStatus status, StudyStep step) {
        given(studyStepRepository.findFirstByCurriculumIdAndStatusOrderByStepOrderAsc(
                CURRICULUM_ID, status)).willReturn(Optional.ofNullable(step));
    }

    /** 완료 후 재조정이 읽는 전체 단계 목록을 준비한다. */
    private void givenAllSteps(StudyStep... steps) {
        given(studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(CURRICULUM_ID))
                .willReturn(List.of(steps));
    }

    /** 첫 단계를 실제로 시작한 상태로 만든다. */
    private StudyStep startedStep(int order, String title, int allocatedMinutes) throws Exception {
        StudyStep step = step(order, title, allocatedMinutes);
        step.start(START);
        session.startStep(order, START);
        curriculum.startProgress(START);
        return step;
    }

    @Nested
    @DisplayName("학습 시작")
    class Start {

        @Test
        @DisplayName("PENDING 단계를 시작하면 진행 중이 되고 세션과 계획 상태가 함께 바뀐다")
        void startsFirstStep() throws Exception {
            StudyStep step = step(1, "프로세스와 스레드", 40);
            givenSession();
            givenCurriculum();
            givenStep(step);
            givenFirst(StudyStepStatus.IN_PROGRESS, null);
            givenFirst(StudyStepStatus.PENDING, step);

            StudyStep started = studyStepService.start(SESSION_CODE, step.getId());

            assertThat(started.getStatus()).isEqualTo(StudyStepStatus.IN_PROGRESS);
            assertThat(started.getStartedAt()).isEqualTo(START);
            assertThat(started.getCompletedAt()).isNull();
            assertThat(started.getActualStudyMinutes()).isNull();
            assertThat(session.getCurrentStepOrder()).isEqualTo(1);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
            assertThat(curriculum.getStatus()).isEqualTo(CurriculumStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("이미 진행 중인 단계를 다시 시작해도 시작 시각을 덮어쓰지 않는다")
        void startIsIdempotent() throws Exception {
            StudyStep step = startedStep(1, "프로세스와 스레드", 40);
            givenSession();
            givenCurriculum();
            givenStep(step);
            clock.moveTo(START.plusMinutes(20));

            StudyStep started = studyStepService.start(SESSION_CODE, step.getId());

            assertThat(started.getStatus()).isEqualTo(StudyStepStatus.IN_PROGRESS);
            assertThat(started.getStartedAt()).isEqualTo(START);
        }

        @Test
        @DisplayName("이미 완료한 단계를 다시 시작하면 거절한다")
        void rejectsCompletedStep() throws Exception {
            StudyStep step = startedStep(1, "프로세스와 스레드", 40);
            clock.moveTo(START.plusMinutes(30));
            step.complete(START.plusMinutes(30));
            givenSession();
            givenCurriculum();
            givenStep(step);

            assertThatThrownBy(() -> studyStepService.start(SESSION_CODE, step.getId()))
                    .isInstanceOf(StudyStepAlreadyCompletedException.class);
        }

        @Test
        @DisplayName("앞선 단계를 건너뛰고 시작할 수 없다")
        void rejectsOutOfOrderStep() throws Exception {
            StudyStep first = step(1, "프로세스와 스레드", 40);
            StudyStep second = step(2, "CPU 스케줄링", 35);
            givenSession();
            givenCurriculum();
            givenStep(second);
            givenFirst(StudyStepStatus.IN_PROGRESS, null);
            givenFirst(StudyStepStatus.PENDING, first);

            assertThatThrownBy(() -> studyStepService.start(SESSION_CODE, second.getId()))
                    .isInstanceOf(InvalidStudyStepOrderException.class);
            assertThat(second.getStatus()).isEqualTo(StudyStepStatus.PENDING);
        }

        @Test
        @DisplayName("다른 단계가 진행 중이면 새 단계를 시작할 수 없다")
        void rejectsWhenAnotherStepIsInProgress() throws Exception {
            StudyStep first = startedStep(1, "프로세스와 스레드", 40);
            StudyStep second = step(2, "CPU 스케줄링", 35);
            givenSession();
            givenCurriculum();
            givenStep(second);
            givenFirst(StudyStepStatus.IN_PROGRESS, first);

            assertThatThrownBy(() -> studyStepService.start(SESSION_CODE, second.getId()))
                    .isInstanceOf(AnotherStepInProgressException.class);
            assertThat(second.getStatus()).isEqualTo(StudyStepStatus.PENDING);
        }

        @Test
        @DisplayName("시험 시각이 지나면 새 단계를 시작할 수 없다")
        void rejectsAfterExamStarted() throws Exception {
            StudyStep step = step(1, "프로세스와 스레드", 40);
            givenSession();
            givenCurriculum();
            givenStep(step);
            clock.moveTo(session.getExamAt().plusMinutes(1));

            assertThatThrownBy(() -> studyStepService.start(SESSION_CODE, step.getId()))
                    .isInstanceOf(ExamAlreadyStartedException.class);
            assertThat(step.getStatus()).isEqualTo(StudyStepStatus.PENDING);
        }

        @Test
        @DisplayName("다른 세션의 단계는 찾을 수 없다")
        void rejectsStepOfAnotherSession() {
            UUID otherSessionStepId = UUID.randomUUID();
            givenSession();
            givenCurriculum();
            given(studyStepRepository.findByIdAndCurriculumId(otherSessionStepId, CURRICULUM_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> studyStepService.start(SESSION_CODE, otherSessionStepId))
                    .isInstanceOf(StudyStepNotFoundException.class);
        }

        @Test
        @DisplayName("학습 계획이 없으면 시작할 수 없다")
        void rejectsWithoutCurriculum() {
            givenSession();
            given(curriculumRepository.findByStudySessionId(SESSION_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> studyStepService.start(SESSION_CODE, UUID.randomUUID()))
                    .isInstanceOf(CurriculumNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("학습 완료")
    class Complete {

        @Test
        @DisplayName("실제 학습시간을 시작 시각과 완료 시각으로 계산한다")
        void recordsActualStudyMinutes() throws Exception {
            StudyStep step = startedStep(1, "프로세스와 스레드", 40);
            StudyStep next = step(2, "CPU 스케줄링", 35);
            givenSession();
            givenCurriculum();
            givenStep(step);
            givenAllSteps(step, next);
            clock.moveTo(START.plusMinutes(37));

            CompletionResult result = studyStepService.complete(SESSION_CODE, step.getId());

            assertThat(step.getStatus()).isEqualTo(StudyStepStatus.COMPLETED);
            assertThat(step.getCompletedAt()).isEqualTo(START.plusMinutes(37));
            assertThat(step.getActualStudyMinutes()).isEqualTo(37);
            assertThat(result.nextStep()).contains(next);
            assertThat(result.curriculumCompleted()).isFalse();
            assertThat(curriculum.getStatus()).isEqualTo(CurriculumStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("1초라도 학습했으면 최소 1분으로 기록한다")
        void recordsAtLeastOneMinute() throws Exception {
            StudyStep step = startedStep(1, "프로세스와 스레드", 40);
            givenSession();
            givenCurriculum();
            givenStep(step);
            givenAllSteps(step);
            clock.moveTo(START.plusSeconds(25));

            studyStepService.complete(SESSION_CODE, step.getId());

            assertThat(step.getActualStudyMinutes()).isEqualTo(1);
        }

        @Test
        @DisplayName("배정 시간을 넘겨도 실제 시간을 그대로 기록한다")
        void keepsActualMinutesOverAllocated() throws Exception {
            StudyStep step = startedStep(1, "프로세스와 스레드", 30);
            givenSession();
            givenCurriculum();
            givenStep(step);
            givenAllSteps(step);
            clock.moveTo(START.plusMinutes(62));

            studyStepService.complete(SESSION_CODE, step.getId());

            assertThat(step.getAllocatedMinutes()).isEqualTo(30);
            assertThat(step.getActualStudyMinutes()).isEqualTo(62);
        }

        @Test
        @DisplayName("완료하면 진행 중인 단계가 없어진다")
        void clearsCurrentStepOrder() throws Exception {
            StudyStep step = startedStep(1, "프로세스와 스레드", 40);
            StudyStep next = step(2, "CPU 스케줄링", 35);
            givenSession();
            givenCurriculum();
            givenStep(step);
            givenAllSteps(step, next);
            clock.moveTo(START.plusMinutes(30));

            studyStepService.complete(SESSION_CODE, step.getId());

            assertThat(session.getCurrentStepOrder()).isNull();
        }

        @Test
        @DisplayName("시작하지 않은 단계는 완료할 수 없다")
        void rejectsNotStartedStep() throws Exception {
            StudyStep step = step(1, "프로세스와 스레드", 40);
            givenSession();
            givenCurriculum();
            givenStep(step);

            assertThatThrownBy(() -> studyStepService.complete(SESSION_CODE, step.getId()))
                    .isInstanceOf(StudyStepNotStartedException.class);
            assertThat(step.getActualStudyMinutes()).isNull();
        }

        @Test
        @DisplayName("이미 완료한 단계를 다시 완료해도 기록이 바뀌지 않는다")
        void completeIsIdempotent() throws Exception {
            StudyStep step = startedStep(1, "프로세스와 스레드", 40);
            StudyStep next = step(2, "CPU 스케줄링", 35);
            givenSession();
            givenCurriculum();
            givenStep(step);
            givenAllSteps(step, next);
            clock.moveTo(START.plusMinutes(30));
            studyStepService.complete(SESSION_CODE, step.getId());

            clock.moveTo(START.plusMinutes(90));
            CompletionResult again = studyStepService.complete(SESSION_CODE, step.getId());

            assertThat(step.getCompletedAt()).isEqualTo(START.plusMinutes(30));
            assertThat(step.getActualStudyMinutes()).isEqualTo(30);
            assertThat(again.nextStep()).contains(next);
        }

        @Test
        @DisplayName("마지막 단계를 완료하면 계획이 끝나고 세션은 진행 중으로 남는다")
        void completesCurriculumOnLastStep() throws Exception {
            StudyStep step = startedStep(3, "입출력", 30);
            givenSession();
            givenCurriculum();
            givenStep(step);
            givenAllSteps(step);
            clock.moveTo(START.plusMinutes(28));

            CompletionResult result = studyStepService.complete(SESSION_CODE, step.getId());

            assertThat(result.nextStep()).isEmpty();
            assertThat(result.curriculumCompleted()).isTrue();
            assertThat(curriculum.getStatus()).isEqualTo(CurriculumStatus.COMPLETED);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("완료하면 남은 학습 시간을 재계산하지만 전체 학습 가능 시간은 그대로 둔다")
        void recalculatesRemainingButKeepsAvailable() throws Exception {
            StudyStep step = startedStep(1, "프로세스와 스레드", 40);
            givenSession();
            givenCurriculum();
            givenStep(step);
            givenAllSteps(step);
            clock.moveTo(START.plusMinutes(50));

            CompletionResult result = studyStepService.complete(SESSION_CODE, step.getId());

            // 예산 기준 180 - 50 = 130, 시험까지 190분 → 130
            assertThat(result.remainingStudyMinutes()).isEqualTo(130);
            assertThat(session.getRemainingStudyMinutes()).isEqualTo(130);
            assertThat(session.getAvailableStudyMinutes()).isEqualTo(180);
        }

        @Test
        @DisplayName("남은 시간이 없으면 남은 단계를 SKIPPED 하고 다음 단계가 없다")
        void skipsPendingWhenNoTimeLeft() throws Exception {
            StudyStep step = startedStep(1, "프로세스와 스레드", 40);
            StudyStep next = step(2, "CPU 스케줄링", 35);
            givenSession();
            givenCurriculum();
            givenStep(step);
            givenAllSteps(step, next);
            // 시험 시각을 넘겨 완료하면 시험 기준 남은 시간이 0이 된다
            clock.moveTo(session.getExamAt().plusMinutes(5));

            CompletionResult result = studyStepService.complete(SESSION_CODE, step.getId());

            assertThat(result.remainingStudyMinutes()).isZero();
            assertThat(next.getStatus()).isEqualTo(StudyStepStatus.SKIPPED);
            assertThat(next.getAllocatedMinutes()).isZero();
            assertThat(next.getSkipReason()).isEqualTo(SkipReason.TIME_CONSTRAINT);
            assertThat(result.nextStep()).isEmpty();
            assertThat(result.curriculumCompleted()).isTrue();
            assertThat(result.reallocation().changed()).isTrue();
        }

        @Test
        @DisplayName("시험 시각이 지나도 진행 중인 단계는 완료할 수 있다")
        void allowsCompleteAfterExamStarted() throws Exception {
            StudyStep step = startedStep(1, "프로세스와 스레드", 40);
            givenSession();
            givenCurriculum();
            givenStep(step);
            givenAllSteps(step);
            clock.moveTo(session.getExamAt().plusMinutes(5));

            studyStepService.complete(SESSION_CODE, step.getId());

            assertThat(step.getStatus()).isEqualTo(StudyStepStatus.COMPLETED);
        }
    }
}
