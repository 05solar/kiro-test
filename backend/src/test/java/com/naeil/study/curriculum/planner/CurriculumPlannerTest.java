package com.naeil.study.curriculum.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.naeil.study.curriculum.entity.PriorityReason;
import com.naeil.study.curriculum.entity.StudyStepType;
import com.naeil.study.curriculum.exception.CurriculumGenerationFailedException;
import com.naeil.study.topic.entity.TopicImportance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

@DisplayName("CurriculumPlanner - 남은 시간 기반 학습 계획")
class CurriculumPlannerTest {

    private static final int MIN_TOPIC_MINUTES = 5;
    private static final int REVIEW_MIN = 10;
    private static final int REVIEW_MAX = 45;

    private final CurriculumPlanner planner =
            new CurriculumPlanner(MIN_TOPIC_MINUTES, REVIEW_MIN, REVIEW_MAX);

    private int nextTopicOrder = 1;

    private PlanningTopic topic(String title, TopicImportance importance, int estimated) {
        return new PlanningTopic(UUID.randomUUID(), title, importance, estimated,
                false, false, false, false, nextTopicOrder++);
    }

    private PlanningTopic topic(
            String title, TopicImportance importance, int estimated,
            boolean professor, boolean pastExam, boolean weak, boolean mustStudy) {
        return new PlanningTopic(UUID.randomUUID(), title, importance, estimated,
                professor, pastExam, weak, mustStudy, nextTopicOrder++);
    }

    private PlanningTopic topicWithOrder(
            String title, TopicImportance importance, int estimated, int order) {
        return new PlanningTopic(UUID.randomUUID(), title, importance, estimated,
                false, false, false, false, order);
    }

    private int allocatedFor(CurriculumPlan plan, PlanningTopic topic) {
        return plan.steps().stream()
                .filter(step -> topic.topicId().equals(step.topicId()))
                .mapToInt(PlannedStep::allocatedMinutes)
                .findFirst()
                .orElse(0);
    }

    private boolean contains(CurriculumPlan plan, PlanningTopic topic) {
        return plan.steps().stream().anyMatch(step -> topic.topicId().equals(step.topicId()));
    }

    private List<PlannedStep> studySteps(CurriculumPlan plan) {
        return plan.steps().stream().filter(step -> !step.isReview()).toList();
    }

    @Nested
    @DisplayName("시간이 충분한 경우")
    class EnoughTime {

        @Test
        @DisplayName("모든 주제를 권장 시간 그대로 배정한다")
        void allocatesEstimatedMinutesToEveryTopic() {
            PlanningTopic a = topic("A", TopicImportance.VERY_HIGH, 30);
            PlanningTopic b = topic("B", TopicImportance.HIGH, 40);
            PlanningTopic c = topic("C", TopicImportance.MEDIUM, 50);

            CurriculumPlan plan = planner.planInitial(180, List.of(a, b, c));

            assertThat(studySteps(plan)).hasSize(3);
            assertThat(allocatedFor(plan, a)).isEqualTo(30);
            assertThat(allocatedFor(plan, b)).isEqualTo(40);
            assertThat(allocatedFor(plan, c)).isEqualTo(50);
            assertThat(plan.totalAllocatedMinutes()).isLessThanOrEqualTo(180);
        }

        @Test
        @DisplayName("남는 시간이 충분하면 마지막에 복습 단계를 만든다")
        void addsReviewStepWithLeftoverTime() {
            PlanningTopic a = topic("A", TopicImportance.VERY_HIGH, 30);
            PlanningTopic b = topic("B", TopicImportance.HIGH, 40);

            CurriculumPlan plan = planner.planInitial(100, List.of(a, b));

            List<PlannedStep> steps = plan.steps();
            assertThat(steps).hasSize(3);
            PlannedStep review = steps.get(steps.size() - 1);
            assertThat(review.type()).isEqualTo(StudyStepType.REVIEW);
            assertThat(review.topicId()).isNull();
            assertThat(review.allocatedMinutes()).isEqualTo(30);
            assertThat(plan.totalAllocatedMinutes()).isEqualTo(100);
        }

        @Test
        @DisplayName("복습 단계는 상한을 넘지 않는다")
        void capsReviewMinutes() {
            PlanningTopic a = topic("A", TopicImportance.VERY_HIGH, 30);

            CurriculumPlan plan = planner.planInitial(300, List.of(a));

            PlannedStep review = plan.steps().get(plan.steps().size() - 1);
            assertThat(review.type()).isEqualTo(StudyStepType.REVIEW);
            assertThat(review.allocatedMinutes()).isEqualTo(REVIEW_MAX);
            // 남는 시간을 억지로 다 쓰지 않는다.
            assertThat(plan.totalAllocatedMinutes()).isEqualTo(30 + REVIEW_MAX);
        }

        @Test
        @DisplayName("남는 시간이 조금이면 복습 단계를 만들지 않는다")
        void skipsReviewWhenLeftoverIsTiny() {
            PlanningTopic a = topic("A", TopicImportance.VERY_HIGH, 30);

            CurriculumPlan plan = planner.planInitial(35, List.of(a));

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).type()).isEqualTo(StudyStepType.STUDY);
            assertThat(plan.totalAllocatedMinutes()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("시간이 부족한 경우")
    class NotEnoughTime {

        @Test
        @DisplayName("우선순위가 높은 주제를 남기고 시간을 줄인다")
        void selectsAndCompressesByPriority() {
            PlanningTopic veryHigh = topic("A", TopicImportance.VERY_HIGH, 60);
            PlanningTopic high = topic("B", TopicImportance.HIGH, 60);
            PlanningTopic medium = topic("C", TopicImportance.MEDIUM, 60);
            PlanningTopic low = topic("D", TopicImportance.LOW, 60);

            CurriculumPlan plan = planner.planInitial(120, List.of(veryHigh, high, medium, low));

            assertThat(plan.totalAllocatedMinutes()).isLessThanOrEqualTo(120);
            assertThat(contains(plan, veryHigh)).isTrue();
            assertThat(contains(plan, high)).isTrue();
            // 중요도가 높은 주제가 더 많은 시간을 받는다
            assertThat(allocatedFor(plan, veryHigh)).isGreaterThanOrEqualTo(allocatedFor(plan, medium));
        }

        @Test
        @DisplayName("중요도별 하한 아래로는 줄이지 않는다")
        void respectsCompressionFloor() {
            PlanningTopic veryHigh = topic("A", TopicImportance.VERY_HIGH, 100);
            PlanningTopic high = topic("B", TopicImportance.HIGH, 100);

            CurriculumPlan plan = planner.planInitial(110, List.of(veryHigh, high));

            assertThat(allocatedFor(plan, veryHigh)).isGreaterThanOrEqualTo(60);
            assertThat(allocatedFor(plan, high)).isGreaterThanOrEqualTo(50);
            assertThat(plan.totalAllocatedMinutes()).isLessThanOrEqualTo(110);
        }

        @Test
        @DisplayName("큰 주제가 안 들어가도 뒤의 짧은 주제는 담는다")
        void keepsGoingWhenBigTopicDoesNotFit() {
            PlanningTopic big = topic("큰 주제", TopicImportance.VERY_HIGH, 120);
            PlanningTopic small = topic("작은 주제", TopicImportance.HIGH, 20);

            CurriculumPlan plan = planner.planInitial(30, List.of(big, small));

            assertThat(contains(plan, big)).isFalse();
            assertThat(contains(plan, small)).isTrue();
            assertThat(plan.totalAllocatedMinutes()).isLessThanOrEqualTo(30);
        }
    }

    @Nested
    @DisplayName("학습 맥락 반영")
    class StudyContextPriority {

        @Test
        @DisplayName("반드시 학습할 주제는 중요도가 낮아도 빠지지 않는다")
        void keepsMustStudyTopicEvenWhenImportanceIsLow() {
            PlanningTopic veryHigh = topic("A", TopicImportance.VERY_HIGH, 60,
                    false, false, false, false);
            PlanningTopic lowMustStudy = topic("B", TopicImportance.LOW, 30,
                    false, false, false, true);

            CurriculumPlan plan = planner.planInitial(60, List.of(veryHigh, lowMustStudy));

            assertThat(contains(plan, lowMustStudy)).isTrue();
            assertThat(plan.totalAllocatedMinutes()).isLessThanOrEqualTo(60);
        }

        @Test
        @DisplayName("반드시 학습할 주제는 mandatory로 표시된다")
        void marksMustStudyAsMandatory() {
            PlanningTopic mustStudy = topic("B", TopicImportance.HIGH, 30,
                    false, false, false, true);

            CurriculumPlan plan = planner.planInitial(60, List.of(mustStudy));

            PlannedStep step = studySteps(plan).get(0);
            assertThat(step.mandatory()).isTrue();
            assertThat(step.priorityReasons()).contains(PriorityReason.MUST_STUDY);
        }

        @Test
        @DisplayName("자신 없는 주제는 같은 중요도의 다른 주제보다 시간이 덜 깎인다")
        void preservesWeakAreaTopic() {
            PlanningTopic normal = topic("A", TopicImportance.HIGH, 50,
                    false, false, false, false);
            PlanningTopic weak = topic("B", TopicImportance.HIGH, 50,
                    false, false, true, false);

            CurriculumPlan plan = planner.planInitial(70, List.of(normal, weak));

            assertThat(allocatedFor(plan, weak)).isGreaterThan(allocatedFor(plan, normal));
            assertThat(plan.totalAllocatedMinutes()).isLessThanOrEqualTo(70);
        }

        @Test
        @DisplayName("교수님이 강조한 주제가 같은 중요도의 일반 주제보다 우선한다")
        void prefersProfessorEmphasisTopic() {
            PlanningTopic normal = topic("A", TopicImportance.HIGH, 50,
                    false, false, false, false);
            PlanningTopic emphasized = topic("B", TopicImportance.HIGH, 50,
                    true, false, false, false);

            CurriculumPlan plan = planner.planInitial(70, List.of(normal, emphasized));

            assertThat(allocatedFor(plan, emphasized)).isGreaterThan(allocatedFor(plan, normal));
        }

        @Test
        @DisplayName("기출 관련 주제가 같은 중요도의 일반 주제보다 우선한다")
        void prefersPastExamTopic() {
            PlanningTopic normal = topic("A", TopicImportance.HIGH, 50,
                    false, false, false, false);
            PlanningTopic pastExam = topic("B", TopicImportance.HIGH, 50,
                    false, true, false, false);

            CurriculumPlan plan = planner.planInitial(70, List.of(normal, pastExam));

            assertThat(allocatedFor(plan, pastExam)).isGreaterThan(allocatedFor(plan, normal));
        }

        @Test
        @DisplayName("선정 이유를 함께 남긴다")
        void recordsPriorityReasons() {
            PlanningTopic topic = topic("A", TopicImportance.VERY_HIGH, 30,
                    true, true, true, true);

            CurriculumPlan plan = planner.planInitial(60, List.of(topic));

            assertThat(studySteps(plan).get(0).priorityReasons()).containsExactlyInAnyOrder(
                    PriorityReason.MUST_STUDY,
                    PriorityReason.CORE_TOPIC,
                    PriorityReason.PROFESSOR_EMPHASIS,
                    PriorityReason.PAST_EXAM,
                    PriorityReason.WEAK_AREA);
        }
    }

    @Nested
    @DisplayName("순서")
    class Ordering {

        @Test
        @DisplayName("최종 순서는 원래 학습 순서를 따른다")
        void keepsOriginalStudyOrder() {
            PlanningTopic first = topicWithOrder("프로세스", TopicImportance.MEDIUM, 30, 1);
            PlanningTopic second = topicWithOrder("CPU 스케줄링", TopicImportance.VERY_HIGH, 30, 2);
            PlanningTopic third = topicWithOrder("교착상태", TopicImportance.HIGH, 30, 3);

            CurriculumPlan plan = planner.planInitial(120, List.of(third, first, second));

            assertThat(studySteps(plan)).extracting(PlannedStep::title)
                    .containsExactly("프로세스", "CPU 스케줄링", "교착상태");
        }

        @Test
        @DisplayName("단계 번호는 1부터 빠짐없이 매겨진다")
        void assignsSequentialOrder() {
            List<PlanningTopic> topics = List.of(
                    topic("A", TopicImportance.HIGH, 20),
                    topic("B", TopicImportance.HIGH, 20),
                    topic("C", TopicImportance.HIGH, 20));

            CurriculumPlan plan = planner.planInitial(120, topics);

            assertThat(plan.steps()).extracting(PlannedStep::order).containsExactly(1, 2, 3, 4);
        }

        @Test
        @DisplayName("복습 단계는 항상 마지막이다")
        void putsReviewLast() {
            CurriculumPlan plan = planner.planInitial(120, List.of(
                    topic("A", TopicImportance.HIGH, 20),
                    topic("B", TopicImportance.HIGH, 20)));

            PlannedStep last = plan.steps().get(plan.steps().size() - 1);
            assertThat(last.type()).isEqualTo(StudyStepType.REVIEW);
            assertThat(studySteps(plan)).allSatisfy(
                    step -> assertThat(step.order()).isLessThan(last.order()));
        }
    }

    @Nested
    @DisplayName("제약 보장")
    class Constraints {

        @Test
        @DisplayName("배정 시간은 권장 시간을 넘지 않는다")
        void neverExceedsEstimatedMinutes() {
            PlanningTopic a = topic("A", TopicImportance.VERY_HIGH, 20);
            PlanningTopic b = topic("B", TopicImportance.HIGH, 30);

            CurriculumPlan plan = planner.planInitial(300, List.of(a, b));

            assertThat(allocatedFor(plan, a)).isEqualTo(20);
            assertThat(allocatedFor(plan, b)).isEqualTo(30);
            assertThat(studySteps(plan)).allSatisfy(step ->
                    assertThat(step.allocatedMinutes()).isLessThanOrEqualTo(step.originalEstimatedMinutes()));
        }

        @Test
        @DisplayName("모든 단계는 최소 학습시간 이상이다")
        void everyStepMeetsMinimumMinutes() {
            List<PlanningTopic> topics = List.of(
                    topic("A", TopicImportance.LOW, 60),
                    topic("B", TopicImportance.LOW, 60),
                    topic("C", TopicImportance.LOW, 60));

            CurriculumPlan plan = planner.planInitial(20, topics);

            assertThat(plan.steps()).allSatisfy(step ->
                    assertThat(step.allocatedMinutes()).isGreaterThanOrEqualTo(MIN_TOPIC_MINUTES));
        }

        @Test
        @DisplayName("같은 주제가 두 번 들어가지 않는다")
        void hasNoDuplicatedTopic() {
            List<PlanningTopic> topics = List.of(
                    topic("A", TopicImportance.HIGH, 30),
                    topic("B", TopicImportance.HIGH, 30));

            CurriculumPlan plan = planner.planInitial(200, topics);

            assertThat(studySteps(plan)).extracting(PlannedStep::topicId).doesNotHaveDuplicates();
        }

        @RepeatedTest(60)
        @DisplayName("어떤 조합에서도 배정 시간의 합이 가용 시간을 넘지 않는다")
        void neverExceedsAvailableMinutes() {
            RandomGenerator random = RandomGenerator.getDefault();
            int topicCount = random.nextInt(1, 12);
            List<PlanningTopic> topics = new ArrayList<>();
            TopicImportance[] importances = TopicImportance.values();
            for (int i = 0; i < topicCount; i++) {
                topics.add(new PlanningTopic(
                        UUID.randomUUID(),
                        "주제 " + i,
                        importances[random.nextInt(importances.length)],
                        random.nextInt(5, 121),
                        random.nextBoolean(),
                        random.nextBoolean(),
                        random.nextBoolean(),
                        random.nextBoolean(),
                        i + 1));
            }
            int available = random.nextInt(MIN_TOPIC_MINUTES, 601);

            CurriculumPlan plan;
            try {
                plan = planner.planInitial(available, topics);
            } catch (CurriculumGenerationFailedException expected) {
                // 남은 시간이 어떤 주제의 최소 학습시간에도 못 미치면 계획을 만들 수 없다.
                // 예: 5분 남았는데 120분짜리 VERY_HIGH 주제 하나뿐이면 최소 72분이 필요하다.
                // 계획을 억지로 만드는 대신 실패로 알리는 것이 이 경우의 정상 동작이다.
                return;
            }

            assertThat(plan.totalAllocatedMinutes()).isLessThanOrEqualTo(available);
            assertThat(plan.steps().stream().mapToInt(PlannedStep::allocatedMinutes).sum())
                    .isEqualTo(plan.totalAllocatedMinutes());
            assertThat(studySteps(plan)).extracting(PlannedStep::topicId).doesNotHaveDuplicates();
        }
    }

    @Nested
    @DisplayName("계획을 만들 수 없는 경우")
    class Failure {

        @Test
        @DisplayName("주제가 없으면 실패한다")
        void failsWithoutTopics() {
            assertThatThrownBy(() -> planner.planInitial(180, List.of()))
                    .isInstanceOf(CurriculumGenerationFailedException.class);
            assertThatThrownBy(() -> planner.planInitial(180, null))
                    .isInstanceOf(CurriculumGenerationFailedException.class);
        }

        @Test
        @DisplayName("남은 시간이 최소 학습시간보다 적으면 실패한다")
        void failsWhenAvailableIsBelowMinimum() {
            PlanningTopic a = topic("A", TopicImportance.VERY_HIGH, 30);

            assertThatThrownBy(() -> planner.planInitial(3, List.of(a)))
                    .isInstanceOf(CurriculumGenerationFailedException.class);
        }

        @Test
        @DisplayName("최소 시간만 확보되면 계획을 만든다")
        void succeedsWithExactlyMinimumMinutes() {
            PlanningTopic a = topic("A", TopicImportance.LOW, 30);

            CurriculumPlan plan = planner.planInitial(MIN_TOPIC_MINUTES, List.of(a));

            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.totalAllocatedMinutes()).isEqualTo(MIN_TOPIC_MINUTES);
        }
    }
}
