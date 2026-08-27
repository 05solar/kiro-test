package com.naeil.study.curriculum.planner;

import com.naeil.study.topic.entity.TopicImportance;
import java.util.Comparator;
import java.util.Map;

/**
 * 학습 계획을 세울 때 쓰는 규칙.
 *
 * <p>정책을 한 곳에 모아 둔다. 여기 있는 숫자는 <b>MVP 휴리스틱</b>이지 학습 과학의
 * 기준이 아니다. 실제 사용 결과를 보고 고칠 값이라 코드 곳곳에 흩어 놓지 않는다.
 */
public final class CurriculumPolicy {

    private CurriculumPolicy() {
    }

    /**
     * 중요도별로 줄일 수 있는 하한 비율.
     *
     * <p>시간이 부족해도 이 비율 아래로는 줄이지 않는다. 10분짜리로 쪼갠 여러 주제보다
     * 제대로 본 몇 개가 낫다는 판단이다.
     *
     * <pre>
     * VERY_HIGH  60%  핵심이라 대충 보면 의미가 없다
     * HIGH       50%
     * MEDIUM     30%
     * LOW         0%  최소 시간까지 줄인다
     * </pre>
     */
    private static final Map<TopicImportance, Double> MINIMUM_RATIO = Map.of(
            TopicImportance.VERY_HIGH, 0.6,
            TopicImportance.HIGH, 0.5,
            TopicImportance.MEDIUM, 0.3,
            TopicImportance.LOW, 0.0);

    /**
     * 선택과 시간 배분에 함께 쓰는 우선순위.
     *
     * <p>숫자 점수를 매기지 않는다. `VERY_HIGH = 100, 교수 강조 = +30` 같은 계수는
     * 근거 없이 정해지고, 한 번 박히면 왜 그 값인지 아무도 설명하지 못한다.
     * 대신 무엇이 무엇보다 앞서는지만 순서로 정한다.
     *
     * <pre>
     * 1. mustStudy          사용자가 반드시 하겠다고 한 것
     * 2. importance         학습 우선순위
     * 3. professorEmphasis  교수님 강조
     * 4. pastExam           기출/예상
     * 5. weakArea           자신 없는 부분
     * 6. topicOrder         원래 학습 순서
     * </pre>
     *
     * <p>이 순서는 선택(무엇을 넣을지)과 배분(누구부터 시간을 늘릴지)에 모두 쓴다.
     * 그래서 자신 없는 주제는 같은 중요도의 다른 주제보다 시간이 덜 깎인다.
     */
    public static Comparator<PlanningTopic> priorityComparator() {
        return Comparator
                .comparing(PlanningTopic::mustStudyMatched, Comparator.reverseOrder())
                .thenComparing(PlanningTopic::importance)
                .thenComparing(PlanningTopic::professorEmphasisMatched, Comparator.reverseOrder())
                .thenComparing(PlanningTopic::pastExamMatched, Comparator.reverseOrder())
                .thenComparing(PlanningTopic::weakAreaMatched, Comparator.reverseOrder())
                .thenComparingInt(PlanningTopic::topicOrder);
    }

    /**
     * 이 주제를 계획에 넣는다면 최소한 배정할 시간.
     *
     * <p>중요도별 하한 비율과 절대 하한 중 큰 값이다. 권장 시간을 넘지 않는다.
     */
    public static int minimumMinutes(PlanningTopic topic, int absoluteMinimum) {
        double ratio = MINIMUM_RATIO.getOrDefault(topic.importance(), 0.0);
        int byRatio = (int) Math.round(topic.estimatedMinutes() * ratio);
        return Math.min(topic.estimatedMinutes(), Math.max(absoluteMinimum, byRatio));
    }
}
