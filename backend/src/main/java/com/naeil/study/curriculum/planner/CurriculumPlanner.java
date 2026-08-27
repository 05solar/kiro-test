package com.naeil.study.curriculum.planner;

import com.naeil.study.curriculum.entity.StudyStepType;
import com.naeil.study.curriculum.exception.CurriculumGenerationFailedException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 남은 시간 안에 들어가는 학습 계획을 만든다.
 *
 * <p><b>규칙 기반이다. AI를 부르지 않는다.</b> 중요도, 예상 시간, 학습 맥락 일치 여부는
 * 이미 분석 단계에서 AI가 정했다. 남은 일은 그 값들을 가지고 시간을 나누는 것이고,
 * 그건 규칙으로 하는 편이 낫다.
 *
 * <pre>
 * 예측 가능하다      같은 입력이면 같은 결과가 나온다
 * 검증 가능하다      시간 총합 같은 제약을 코드가 보장한다
 * 비용이 들지 않는다  자료를 다시 보낼 이유가 없다
 * </pre>
 *
 * LLM에게 "180분에 맞춰 나눠 줘"라고 맡기면 합이 200분이 되거나, 반드시 넣어야 할
 * 주제가 조용히 빠진다. 그런 실수는 사용자가 알아채기 어렵다.
 *
 * <p><b>세 단계로 나눈다.</b>
 * <pre>
 * 1. 선택   우선순위 순으로, 최소 시간을 확보할 수 있는 주제만 담는다
 * 2. 배분   남은 시간을 우선순위 순으로 나눠 권장 시간까지 늘린다
 * 3. 정렬   원래 학습 순서로 되돌리고, 시간이 남으면 복습 단계를 붙인다
 * </pre>
 *
 * 선택과 배분에 같은 우선순위를 쓴다. 그래서 자신 없다고 표시한 주제는 같은 중요도의
 * 다른 주제보다 시간이 덜 깎인다. 별도 보정 규칙을 두지 않아도 그렇게 된다.
 *
 * <p><b>최종 순서는 원래 학습 순서를 따른다.</b> 중요하다고 뒤 내용을 앞으로 끌어오면
 * 선수 개념 없이 공부하게 된다. 중요도는 "무엇을 넣을지"에만 쓰고,
 * "어떤 순서로 볼지"는 원래 순서를 지킨다.
 */
@Component
public class CurriculumPlanner {

    private static final Logger log = LoggerFactory.getLogger(CurriculumPlanner.class);

    private static final String REVIEW_TITLE = "핵심 개념 최종 복습";

    private final int minTopicMinutes;
    private final int reviewMinMinutes;
    private final int reviewMaxMinutes;

    public CurriculumPlanner(
            @Value("${curriculum.min-topic-minutes:5}") int minTopicMinutes,
            @Value("${curriculum.review-min-minutes:10}") int reviewMinMinutes,
            @Value("${curriculum.review-max-minutes:45}") int reviewMaxMinutes
    ) {
        this.minTopicMinutes = minTopicMinutes;
        this.reviewMinMinutes = reviewMinMinutes;
        this.reviewMaxMinutes = reviewMaxMinutes;
    }

    public int minTopicMinutes() {
        return minTopicMinutes;
    }

    /**
     * 최초 학습 계획을 만든다.
     *
     * @param availableMinutes 쓸 수 있는 학습 시간(분)
     * @param topics           분석으로 만들어진 주제들
     * @throws CurriculumGenerationFailedException 유효한 계획을 만들 수 없는 경우
     */
    public CurriculumPlan planInitial(int availableMinutes, List<PlanningTopic> topics) {
        if (topics == null || topics.isEmpty()) {
            throw new CurriculumGenerationFailedException("no topics to plan");
        }
        if (availableMinutes < minTopicMinutes) {
            throw new CurriculumGenerationFailedException(
                    "available minutes below minimum: " + availableMinutes);
        }

        List<PlanningTopic> byPriority = topics.stream()
                .sorted(CurriculumPolicy.priorityComparator())
                .toList();

        Map<UUID, Integer> allocation = selectWithMinimum(byPriority, availableMinutes);
        if (allocation.isEmpty()) {
            throw new CurriculumGenerationFailedException("no topic fits into available minutes");
        }

        int used = growToEstimated(byPriority, allocation, availableMinutes);
        List<PlannedStep> steps = toStepsInStudyOrder(topics, allocation);

        int leftover = availableMinutes - used;
        if (leftover >= reviewMinMinutes) {
            int reviewMinutes = Math.min(leftover, reviewMaxMinutes);
            steps.add(reviewStep(reviewMinutes, steps.size() + 1));
            used += reviewMinutes;
        }

        CurriculumPlan plan = new CurriculumPlan(List.copyOf(steps), used);
        verify(plan, availableMinutes, topics);
        log.info("curriculum planned: available={}, allocated={}, steps={}, topics={}/{}",
                availableMinutes, used, steps.size(), allocation.size(), topics.size());
        return plan;
    }

    /**
     * 우선순위 순으로 최소 시간을 확보할 수 있는 주제를 담는다.
     *
     * <p>앞 주제가 안 들어간다고 멈추지 않는다. 뒤에 더 짧은 주제가 있으면 그건 들어갈 수 있다.
     * 시간이 아주 부족할 때 큰 주제 하나 때문에 계획이 텅 비는 것을 막는다.
     */
    private Map<UUID, Integer> selectWithMinimum(List<PlanningTopic> byPriority, int availableMinutes) {
        Map<UUID, Integer> allocation = new LinkedHashMap<>();
        int remaining = availableMinutes;

        for (PlanningTopic topic : byPriority) {
            int minimum = CurriculumPolicy.minimumMinutes(topic, minTopicMinutes);
            if (minimum <= remaining) {
                allocation.put(topic.topicId(), minimum);
                remaining -= minimum;
            }
        }
        return allocation;
    }

    /**
     * 남은 시간을 우선순위 순으로 나눠 준다. 권장 시간을 넘기지 않는다.
     *
     * <p>최초 계획에서 권장 시간보다 더 주지 않는 이유는, 그 시간을 다른 주제에 쓰는 편이
     * 낫기 때문이다. 여유가 있으면 마지막 복습 단계로 간다.
     *
     * @return 실제로 배정한 시간의 합
     */
    private int growToEstimated(
            List<PlanningTopic> byPriority, Map<UUID, Integer> allocation, int availableMinutes) {
        int used = allocation.values().stream().mapToInt(Integer::intValue).sum();
        int remaining = availableMinutes - used;

        for (PlanningTopic topic : byPriority) {
            if (remaining <= 0) {
                break;
            }
            Integer current = allocation.get(topic.topicId());
            if (current == null) {
                continue;
            }
            int room = topic.estimatedMinutes() - current;
            if (room <= 0) {
                continue;
            }
            int given = Math.min(room, remaining);
            allocation.put(topic.topicId(), current + given);
            remaining -= given;
            used += given;
        }
        return used;
    }

    /**
     * 선택된 주제를 원래 학습 순서로 되돌려 단계로 만든다.
     */
    private List<PlannedStep> toStepsInStudyOrder(
            List<PlanningTopic> topics, Map<UUID, Integer> allocation) {
        List<PlannedStep> steps = new ArrayList<>();
        List<PlanningTopic> selected = topics.stream()
                .filter(topic -> allocation.containsKey(topic.topicId()))
                .sorted(Comparator.comparingInt(PlanningTopic::topicOrder))
                .toList();

        int order = 1;
        for (PlanningTopic topic : selected) {
            steps.add(new PlannedStep(
                    topic.topicId(),
                    topic.title(),
                    StudyStepType.STUDY,
                    allocation.get(topic.topicId()),
                    topic.estimatedMinutes(),
                    topic.mustStudyMatched(),
                    topic.priorityReasons(),
                    order++));
        }
        return steps;
    }

    private PlannedStep reviewStep(int minutes, int order) {
        return new PlannedStep(null, REVIEW_TITLE, StudyStepType.REVIEW,
                minutes, minutes, false, List.of(), order);
    }

    /**
     * 계획이 지켜야 할 조건을 확인한다.
     *
     * <p>규칙으로 만들었으니 깨질 리 없다고 두지 않는다. 시간 총합을 넘기는 계획은
     * 사용자에게 실행 불가능한 일정을 주는 것이고, 조용히 나가면 알아채기 어렵다.
     */
    private void verify(CurriculumPlan plan, int availableMinutes, List<PlanningTopic> topics) {
        if (plan.steps().isEmpty()) {
            throw new CurriculumGenerationFailedException("plan has no steps");
        }
        if (plan.totalAllocatedMinutes() > availableMinutes) {
            throw new CurriculumGenerationFailedException(
                    "allocated exceeds available: " + plan.totalAllocatedMinutes() + " > " + availableMinutes);
        }

        Map<UUID, Integer> estimatedById = new LinkedHashMap<>();
        topics.forEach(topic -> estimatedById.put(topic.topicId(), topic.estimatedMinutes()));

        List<UUID> seen = new ArrayList<>();
        int sum = 0;
        for (int i = 0; i < plan.steps().size(); i++) {
            PlannedStep step = plan.steps().get(i);
            if (step.order() != i + 1) {
                throw new CurriculumGenerationFailedException("step order is not sequential");
            }
            if (step.allocatedMinutes() < minTopicMinutes) {
                throw new CurriculumGenerationFailedException(
                        "step below minimum minutes: " + step.allocatedMinutes());
            }
            sum += step.allocatedMinutes();

            if (step.isReview()) {
                continue;
            }
            Integer estimated = estimatedById.get(step.topicId());
            if (estimated == null) {
                throw new CurriculumGenerationFailedException("unknown topic in plan: " + step.topicId());
            }
            if (step.allocatedMinutes() > estimated) {
                throw new CurriculumGenerationFailedException(
                        "allocated exceeds estimated for topic: " + step.topicId());
            }
            if (seen.contains(step.topicId())) {
                throw new CurriculumGenerationFailedException("duplicated topic in plan: " + step.topicId());
            }
            seen.add(step.topicId());
        }
        if (sum != plan.totalAllocatedMinutes()) {
            throw new CurriculumGenerationFailedException("total allocated minutes mismatch");
        }
    }
}
