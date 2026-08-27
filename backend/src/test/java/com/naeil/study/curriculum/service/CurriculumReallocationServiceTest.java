package com.naeil.study.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.curriculum.entity.Curriculum;
import com.naeil.study.curriculum.entity.SkipReason;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.entity.StudyStepStatus;
import com.naeil.study.curriculum.planner.CurriculumPlanner;
import com.naeil.study.curriculum.service.CurriculumReallocationService.ReallocationOutcome;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CurriculumReallocationService - 완료 후 재조정 반영")
class CurriculumReallocationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 19, 0, 0);

    private CurriculumReallocationService service;
    private StudySession session;
    private Curriculum curriculum;
    private int nextOrder;

    @BeforeEach
    void setUp() {
        service = new CurriculumReallocationService(
                new StudyTimeCalculator(), new CurriculumPlanner(5, 10, 45));
        session = StudySession.create("7K2M9QXF", NOW.minusHours(5), 30L);
        // 시험은 멀리 두어 사용자 예산(available - 실제학습)이 남은 시간을 결정하게 한다
        session.updateExamInfo("운영체제", NOW.plusHours(10), 180, 180, NOW.minusHours(4));
        curriculum = Curriculum.create(session, 180, 180, NOW.minusHours(1));
        nextOrder = 1;
    }

    private void setId(Object target, Class<?> type, UUID id) {
        try {
            Field field = type.getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Topic topic(TopicImportance importance, int estimated, boolean weak) {
        Topic topic = Topic.create(session, "주제", "요약", List.of("개념"), importance, estimated,
                false, false, weak, false, List.of(), nextOrder, NOW.minusHours(2));
        setId(topic, Topic.class, UUID.randomUUID());
        return topic;
    }

    private StudyStep pendingStudy(TopicImportance importance, int original, int allocated, boolean weak) {
        StudyStep step = StudyStep.study(curriculum, topic(importance, original, weak),
                nextOrder++, "단계", allocated, original, false, List.of(), NOW.minusHours(1));
        setId(step, StudyStep.class, UUID.randomUUID());
        return step;
    }

    private StudyStep pendingReview(int allocated) {
        StudyStep step = StudyStep.review(curriculum, nextOrder++, "복습", allocated, NOW.minusHours(1));
        setId(step, StudyStep.class, UUID.randomUUID());
        return step;
    }

    /** 실제 학습시간이 채워진 완료 단계. 시작과 완료 사이를 {@code actualMinutes} 로 둔다. */
    private StudyStep completedStudy(int allocated, int actualMinutes) {
        StudyStep step = StudyStep.study(curriculum, topic(TopicImportance.VERY_HIGH, allocated, false),
                nextOrder++, "완료 단계", allocated, allocated, false, List.of(), NOW.minusHours(3));
        setId(step, StudyStep.class, UUID.randomUUID());
        step.start(NOW.minusHours(2));
        step.complete(NOW.minusHours(2).plusMinutes(actualMinutes));
        return step;
    }

    @Test
    @DisplayName("남은 시간이 줄면 배정을 줄이고, 최소 시간도 못 채우면 SKIPPED 한다")
    void shrinksAndSkips() {
        // 완료 실제 150분 → 예산 남은 시간 = 180 - 150 = 30
        StudyStep done = completedStudy(60, 150);
        StudyStep veryHigh = pendingStudy(TopicImportance.VERY_HIGH, 60, 60, false); // 최소 36 > 30 → SKIP
        StudyStep low = pendingStudy(TopicImportance.LOW, 40, 40, false);
        StudyStep review = pendingReview(30);

        ReallocationOutcome outcome = service.reallocate(
                session, curriculum, List.of(done, veryHigh, low, review), NOW);

        assertThat(outcome.remainingStudyMinutes()).isEqualTo(30);
        assertThat(session.getRemainingStudyMinutes()).isEqualTo(30);
        assertThat(outcome.changed()).isTrue();

        assertThat(veryHigh.getStatus()).isEqualTo(StudyStepStatus.SKIPPED);
        assertThat(veryHigh.getAllocatedMinutes()).isZero();
        assertThat(veryHigh.getSkipReason()).isEqualTo(SkipReason.TIME_CONSTRAINT);

        assertThat(low.getStatus()).isEqualTo(StudyStepStatus.PENDING);
        assertThat(low.getAllocatedMinutes()).isGreaterThanOrEqualTo(5);
        assertThat(review.getStatus()).isEqualTo(StudyStepStatus.PENDING);

        // PENDING 배정 합은 남은 시간을 넘지 않는다
        int pendingSum = low.getAllocatedMinutes() + review.getAllocatedMinutes();
        assertThat(pendingSum).isLessThanOrEqualTo(30);
    }

    @Test
    @DisplayName("완료 단계와 진행 중 단계는 재조정으로 바뀌지 않는다")
    void leavesCompletedAndInProgressUntouched() {
        StudyStep done = completedStudy(60, 30);
        StudyStep inProgress = pendingStudy(TopicImportance.HIGH, 50, 50, false);
        inProgress.start(NOW.minusMinutes(10));
        StudyStep pending = pendingStudy(TopicImportance.HIGH, 40, 40, false);

        service.reallocate(session, curriculum, List.of(done, inProgress, pending), NOW);

        assertThat(done.getStatus()).isEqualTo(StudyStepStatus.COMPLETED);
        assertThat(done.getAllocatedMinutes()).isEqualTo(60);
        assertThat(done.getActualStudyMinutes()).isEqualTo(30);
        assertThat(inProgress.getStatus()).isEqualTo(StudyStepStatus.IN_PROGRESS);
        assertThat(inProgress.getAllocatedMinutes()).isEqualTo(50);
    }

    @Test
    @DisplayName("총 배정 시간은 SKIPPED 를 뺀 모든 단계의 배정 합으로 갱신된다")
    void updatesTotalAllocatedMinutes() {
        StudyStep done = completedStudy(60, 150);
        StudyStep veryHigh = pendingStudy(TopicImportance.VERY_HIGH, 60, 60, false); // SKIP 예상
        StudyStep low = pendingStudy(TopicImportance.LOW, 40, 40, false);
        StudyStep review = pendingReview(30);

        service.reallocate(session, curriculum, List.of(done, veryHigh, low, review), NOW);

        int expected = done.getAllocatedMinutes()
                + veryHigh.getAllocatedMinutes()  // 0 (SKIPPED)
                + low.getAllocatedMinutes()
                + review.getAllocatedMinutes();
        assertThat(curriculum.getTotalAllocatedMinutes()).isEqualTo(expected);
        assertThat(veryHigh.getAllocatedMinutes()).isZero();
    }

    @Test
    @DisplayName("변화가 없으면 changed 는 false 이고 이전·현재 값을 함께 담는다")
    void reportsNoChangeWhenAllocationsStay() {
        // 완료 없음 → 예산 180, 남은 PENDING 이 이미 권장 시간이라 늘릴 여지가 없다
        StudyStep a = pendingStudy(TopicImportance.VERY_HIGH, 60, 60, false);
        StudyStep b = pendingStudy(TopicImportance.HIGH, 40, 40, false);

        ReallocationOutcome outcome = service.reallocate(session, curriculum, List.of(a, b), NOW);

        assertThat(outcome.remainingStudyMinutes()).isEqualTo(180);
        assertThat(outcome.changed()).isFalse();
        assertThat(outcome.steps()).hasSize(2);
        assertThat(outcome.steps()).allSatisfy(change ->
                assertThat(change.previousAllocatedMinutes()).isEqualTo(change.allocatedMinutes()));
        assertThat(a.getAllocatedMinutes()).isEqualTo(60);
        assertThat(b.getAllocatedMinutes()).isEqualTo(40);
    }
}
