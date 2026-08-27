package com.naeil.study.curriculum.planner;

import com.naeil.study.curriculum.entity.PriorityReason;
import com.naeil.study.curriculum.entity.StudyStepType;
import java.util.List;
import java.util.UUID;

/**
 * 계획 결과의 한 단계. 아직 저장되지 않은 값이다.
 *
 * @param topicId {@code REVIEW} 단계에는 없다
 * @param order   1부터 시작하는 순서
 */
public record PlannedStep(
        UUID topicId,
        String title,
        StudyStepType type,
        int allocatedMinutes,
        int originalEstimatedMinutes,
        boolean mandatory,
        List<PriorityReason> priorityReasons,
        int order
) {

    public boolean isReview() {
        return type == StudyStepType.REVIEW;
    }
}
