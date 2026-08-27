package com.naeil.study.curriculum.planner;

import com.naeil.study.curriculum.entity.StudyStepStatus;
import java.util.UUID;

/**
 * 재조정 결과의 한 단계. 아직 저장되지 않은 값이다.
 *
 * <p>{@code status} 는 {@link StudyStepStatus#PENDING}(시간이 다시 배정됨) 또는
 * {@link StudyStepStatus#SKIPPED}(시간 부족으로 제외됨) 중 하나다. 제외된 단계의
 * {@code allocatedMinutes} 는 0이다.
 */
public record ReallocatedStep(
        UUID stepId,
        int allocatedMinutes,
        StudyStepStatus status
) {
}
