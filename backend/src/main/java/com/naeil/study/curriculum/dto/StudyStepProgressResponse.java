package com.naeil.study.curriculum.dto;

import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.entity.StudyStepStatus;
import com.naeil.study.curriculum.entity.StudyStepType;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 학습 단계의 진행 상태 응답.
 *
 * <p>시작 / 완료 API가 쓴다. 계획 전체 조회({@link StudyStepResponse})와 달리
 * 중요도나 선정 이유 같은 계획 근거는 담지 않는다. 진행 중 화면에 필요한 것은
 * 남은 시간과 지금 상태이기 때문이다.
 *
 * @param allocatedMinutes   이 단계에 배정된 시간(분)
 * @param actualStudyMinutes 실제로 쓴 시간(분). 완료 전에는 없다
 */
public record StudyStepProgressResponse(
        UUID stepId,
        int stepOrder,
        StudyStepType type,
        String title,
        StudyStepStatus status,
        int allocatedMinutes,
        Integer actualStudyMinutes,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {

    public static StudyStepProgressResponse from(StudyStep step) {
        return new StudyStepProgressResponse(
                step.getId(),
                step.getStepOrder(),
                step.getType(),
                step.getTitle(),
                step.getStatus(),
                step.getAllocatedMinutes(),
                step.getActualStudyMinutes(),
                step.getStartedAt(),
                step.getCompletedAt()
        );
    }
}
