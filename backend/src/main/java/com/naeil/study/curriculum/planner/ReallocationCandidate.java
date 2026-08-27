package com.naeil.study.curriculum.planner;

import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.entity.StudyStepType;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import java.util.UUID;

/**
 * 동적 재조정 대상이 되는 {@code PENDING} 단계의 정보만 담은 값.
 *
 * <p>{@link PlanningTopic} 과 마찬가지로 Planner 를 JPA 엔티티에 묶지 않는다. 덕분에 재조정
 * 알고리즘을 DB 없이 단위 테스트할 수 있다.
 *
 * <p>학습 맥락 일치 여부는 {@link Topic} 을 기준으로 삼는다. 계획 시점에 스냅샷해 둔
 * {@code priorityReasons} 대신 원본 Topic 을 쓰는 이유는, 재조정에서 필요한 것이 "표시용 사유"가
 * 아니라 우선순위 판단에 쓰는 boolean 값이기 때문이다. {@code REVIEW} 단계는 Topic 이 없으므로
 * 중요도와 맥락 값이 모두 비어 있고, 이 경우 우선순위에서 가장 낮게 다룬다.
 *
 * @param importance {@code REVIEW} 단계에는 없다(null)
 */
public record ReallocationCandidate(
        UUID stepId,
        StudyStepType type,
        TopicImportance importance,
        int currentAllocatedMinutes,
        int originalEstimatedMinutes,
        boolean mandatory,
        boolean professorEmphasisMatched,
        boolean pastExamMatched,
        boolean weakAreaMatched,
        int stepOrder
) {

    public static ReallocationCandidate from(StudyStep step) {
        Topic topic = step.getTopic();
        return new ReallocationCandidate(
                step.getId(),
                step.getType(),
                topic == null ? null : topic.getImportance(),
                step.getAllocatedMinutes(),
                step.getOriginalEstimatedMinutes(),
                step.isMandatory(),
                topic != null && topic.isProfessorEmphasisMatched(),
                topic != null && topic.isPastExamMatched(),
                topic != null && topic.isWeakAreaMatched(),
                step.getStepOrder());
    }

    public boolean isReview() {
        return type == StudyStepType.REVIEW;
    }
}
