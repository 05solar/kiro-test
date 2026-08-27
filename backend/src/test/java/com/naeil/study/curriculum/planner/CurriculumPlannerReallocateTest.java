package com.naeil.study.curriculum.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.curriculum.entity.StudyStepStatus;
import com.naeil.study.curriculum.entity.StudyStepType;
import com.naeil.study.topic.entity.TopicImportance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

@DisplayName("CurriculumPlanner.reallocate - 남은 PENDING 단계 동적 재배분")
class CurriculumPlannerReallocateTest {

    private static final int MIN_TOPIC_MINUTES = 5;
    private static final int REVIEW_MIN = 10;
    private static final int REVIEW_MAX = 45;

    private final CurriculumPlanner planner =
            new CurriculumPlanner(MIN_TOPIC_MINUTES, REVIEW_MIN, REVIEW_MAX);

    private int nextOrder = 1;

    private ReallocationCandidate study(TopicImportance importance, int original, int current) {
        return new ReallocationCandidate(UUID.randomUUID(), StudyStepType.STUDY, importance,
                current, original, false, false, false, false, nextOrder++);
    }

    private ReallocationCandidate mandatoryStudy(TopicImportance importance, int original, int current) {
        return new ReallocationCandidate(UUID.randomUUID(), StudyStepType.STUDY, importance,
                current, original, true, false, false, false, nextOrder++);
    }

    private ReallocationCandidate weakStudy(TopicImportance importance, int original, int current) {
        return new ReallocationCandidate(UUID.randomUUID(), StudyStepType.STUDY, importance,
                current, original, false, false, false, true, nextOrder++);
    }

    private ReallocationCandidate review(int original, int current) {
        return new ReallocationCandidate(UUID.randomUUID(), StudyStepType.REVIEW, null,
                current, original, false, false, false, false, nextOrder++);
    }

    private ReallocatedStep resultFor(ReallocationResult result, ReallocationCandidate candidate) {
        return result.steps().stream()
                .filter(step -> step.stepId().equals(candidate.stepId()))
                .findFirst()
                .orElseThrow();
    }

    private int alloc(ReallocationResult result, ReallocationCandidate candidate) {
        return resultFor(result, candidate).allocatedMinutes();
    }

    private boolean skipped(ReallocationResult result, ReallocationCandidate candidate) {
        return resultFor(result, candidate).status() == StudyStepStatus.SKIPPED;
    }

    @Nested
    @DisplayName("시간이 줄어든 경우")
    class Shrink {

        @Test
        @DisplayName("배정 시간 합은 남은 시간을 넘지 않는다")
        void neverExceedsRemaining() {
            ReallocationCandidate a = study(TopicImportance.HIGH, 40, 40);
            ReallocationCandidate b = study(TopicImportance.HIGH, 40, 40);
            ReallocationCandidate c = study(TopicImportance.HIGH, 40, 40);

            ReallocationResult result = planner.reallocate(90, List.of(a, b, c));

            assertThat(result.totalAllocatedMinutes()).isLessThanOrEqualTo(90);
            assertThat(skipped(result, a)).isFalse();
            assertThat(skipped(result, b)).isFalse();
            assertThat(skipped(result, c)).isFalse();
        }

        @Test
        @DisplayName("최소 학습시간을 확보할 수 없는 단계는 SKIPPED 된다")
        void skipsStepBelowMinimum() {
            ReallocationCandidate a = study(TopicImportance.LOW, 60, 60);
            ReallocationCandidate b = study(TopicImportance.LOW, 60, 60);
            ReallocationCandidate c = study(TopicImportance.LOW, 60, 60);

            // 최소 5분씩, 셋을 모두 살리려면 15분이 필요한데 12분뿐이다
            ReallocationResult result = planner.reallocate(12, List.of(a, b, c));

            assertThat(skipped(result, c)).isTrue();
            assertThat(alloc(result, c)).isZero();
            assertThat(alloc(result, a)).isGreaterThanOrEqualTo(MIN_TOPIC_MINUTES);
            assertThat(alloc(result, b)).isGreaterThanOrEqualTo(MIN_TOPIC_MINUTES);
            assertThat(result.totalAllocatedMinutes()).isLessThanOrEqualTo(12);
        }

        @Test
        @DisplayName("반드시 학습할 단계는 중요도가 낮아도 먼저 살린다")
        void keepsMandatoryOverHigherImportance() {
            ReallocationCandidate lowMandatory = mandatoryStudy(TopicImportance.LOW, 20, 20);
            ReallocationCandidate high = study(TopicImportance.HIGH, 20, 20);

            // 하나의 최소 시간만 확보되는 상황: LOW min 5, HIGH min 10, 남은 시간 10
            ReallocationResult result = planner.reallocate(10, List.of(lowMandatory, high));

            assertThat(skipped(result, lowMandatory)).isFalse();
            assertThat(skipped(result, high)).isTrue();
        }

        @Test
        @DisplayName("복습 단계를 일반 STUDY 단계보다 먼저 줄인다")
        void shrinksReviewBeforeStudy() {
            ReallocationCandidate a = study(TopicImportance.HIGH, 40, 40);
            ReallocationCandidate b = study(TopicImportance.HIGH, 40, 40);
            ReallocationCandidate review = review(30, 30);

            ReallocationResult result = planner.reallocate(90, List.of(a, b, review));

            assertThat(alloc(result, a)).isEqualTo(40);
            assertThat(alloc(result, b)).isEqualTo(40);
            assertThat(alloc(result, review)).isEqualTo(REVIEW_MIN);
            assertThat(result.totalAllocatedMinutes()).isEqualTo(90);
        }

        @Test
        @DisplayName("남은 시간이 0이면 모든 단계가 SKIPPED 된다")
        void skipsEverythingWhenNoTimeLeft() {
            ReallocationCandidate a = study(TopicImportance.VERY_HIGH, 40, 40);
            ReallocationCandidate b = study(TopicImportance.HIGH, 40, 40);

            ReallocationResult result = planner.reallocate(0, List.of(a, b));

            assertThat(skipped(result, a)).isTrue();
            assertThat(skipped(result, b)).isTrue();
            assertThat(result.totalAllocatedMinutes()).isZero();
        }
    }

    @Nested
    @DisplayName("시간이 늘어난 경우")
    class Grow {

        @Test
        @DisplayName("확보된 시간을 취약 단계에 먼저 배정한다")
        void addsTimeToWeakAreaFirst() {
            ReallocationCandidate weak = weakStudy(TopicImportance.HIGH, 50, 25);
            ReallocationCandidate normal = study(TopicImportance.HIGH, 50, 25);

            ReallocationResult result = planner.reallocate(70, List.of(weak, normal));

            assertThat(alloc(result, weak)).isGreaterThan(alloc(result, normal));
            assertThat(result.totalAllocatedMinutes()).isLessThanOrEqualTo(70);
        }

        @Test
        @DisplayName("STUDY 배정 시간은 권장 시간을 넘지 않는다")
        void neverExceedsOriginalEstimated() {
            ReallocationCandidate a = study(TopicImportance.VERY_HIGH, 50, 25);

            ReallocationResult result = planner.reallocate(1000, List.of(a));

            assertThat(alloc(result, a)).isEqualTo(50);
        }

        @Test
        @DisplayName("STUDY 를 원래 시간까지 복구한 뒤 남으면 복습에 붙이고 나머지는 남긴다")
        void restoresStudyThenReviewWithoutForcingLeftover() {
            ReallocationCandidate a = study(TopicImportance.HIGH, 50, 25);
            ReallocationCandidate review = review(30, 30);

            ReallocationResult result = planner.reallocate(200, List.of(a, review));

            assertThat(alloc(result, a)).isEqualTo(50);
            assertThat(alloc(result, review)).isEqualTo(REVIEW_MAX);
            // 남는 시간을 억지로 다 쓰지 않는다
            assertThat(result.totalAllocatedMinutes()).isEqualTo(50 + REVIEW_MAX);
        }
    }

    @Nested
    @DisplayName("경계")
    class EdgeCases {

        @Test
        @DisplayName("재조정 대상이 없으면 빈 결과를 돌려준다")
        void emptyCandidates() {
            ReallocationResult result = planner.reallocate(100, List.of());

            assertThat(result.steps()).isEmpty();
            assertThat(result.totalAllocatedMinutes()).isZero();
        }

        @RepeatedTest(80)
        @DisplayName("어떤 조합에서도 핵심 불변조건을 지킨다")
        void holdsInvariants() {
            RandomGenerator random = RandomGenerator.getDefault();
            int count = random.nextInt(1, 10);
            TopicImportance[] importances = TopicImportance.values();
            List<ReallocationCandidate> candidates = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                boolean isReview = random.nextInt(5) == 0;
                int original = random.nextInt(5, 121);
                int current = random.nextInt(1, original + 1);
                if (isReview) {
                    candidates.add(new ReallocationCandidate(UUID.randomUUID(),
                            StudyStepType.REVIEW, null, current, original,
                            false, false, false, false, i + 1));
                } else {
                    candidates.add(new ReallocationCandidate(UUID.randomUUID(),
                            StudyStepType.STUDY, importances[random.nextInt(importances.length)],
                            current, original, random.nextBoolean(), random.nextBoolean(),
                            random.nextBoolean(), random.nextBoolean(), i + 1));
                }
            }
            int remaining = random.nextInt(0, 400);

            ReallocationResult result = planner.reallocate(remaining, candidates);

            // 배정 시간 합은 남은 시간을 넘지 않는다
            assertThat(result.totalAllocatedMinutes()).isLessThanOrEqualTo(remaining);
            int sum = result.steps().stream().mapToInt(ReallocatedStep::allocatedMinutes).sum();
            assertThat(sum).isEqualTo(result.totalAllocatedMinutes());
            // 입력 단계 수와 결과 단계 수가 같다 (자리를 유지한다)
            assertThat(result.steps()).hasSameSizeAs(candidates);

            for (ReallocationCandidate candidate : candidates) {
                ReallocatedStep step = resultFor(result, candidate);
                if (step.status() == StudyStepStatus.SKIPPED) {
                    assertThat(step.allocatedMinutes()).isZero();
                    continue;
                }
                assertThat(step.status()).isEqualTo(StudyStepStatus.PENDING);
                if (candidate.isReview()) {
                    assertThat(step.allocatedMinutes()).isLessThanOrEqualTo(REVIEW_MAX);
                } else {
                    assertThat(step.allocatedMinutes())
                            .isGreaterThanOrEqualTo(MIN_TOPIC_MINUTES)
                            .isLessThanOrEqualTo(candidate.originalEstimatedMinutes());
                }
            }
        }
    }
}
