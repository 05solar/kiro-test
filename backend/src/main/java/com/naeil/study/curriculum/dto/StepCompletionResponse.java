package com.naeil.study.curriculum.dto;

import com.naeil.study.curriculum.entity.StudyStepStatus;
import com.naeil.study.curriculum.service.CurriculumReallocationService.ReallocationOutcome;
import com.naeil.study.curriculum.service.CurriculumReallocationService.StepChange;
import com.naeil.study.curriculum.service.StudyStepService.CompletionResult;
import java.util.List;
import java.util.UUID;

/**
 * 학습 단계 완료 응답.
 *
 * <p>완료 직후 화면이 필요한 것을 한 응답에 모은다.
 * <pre>
 * completedStep  방금 완료한 단계
 * time           재계산된 남은 학습 시간
 * reallocation   남은 단계를 어떻게 다시 배정했는지 (이전 → 현재)
 * nextStep       재배분까지 반영한 뒤 다음에 시작할 수 있는 단계
 * </pre>
 *
 * <p>다음 단계를 <b>자동으로 시작하지는 않는다.</b> 완료 버튼을 누른 시점과 실제로 다음 공부를
 * 시작하는 시점은 다르고, 그 차이가 실제 학습시간에 그대로 들어가기 때문이다.
 *
 * <p>{@code reallocation.steps} 는 재배분 대상이던 단계의 이전·현재 값을 함께 담아, 화면에서
 * "50분 → 40분", "시간 부족으로 제외" 같은 변화를 보여줄 수 있게 한다.
 *
 * @param nextStep            다음에 시작할 수 있는 단계. 수행 가능한 단계가 없으면 null
 * @param curriculumCompleted 수행 가능한 {@code PENDING} 단계가 없으면 true. 세션 종료는 아니다
 */
public record StepCompletionResponse(
        StudyStepProgressResponse completedStep,
        TimeResponse time,
        ReallocationResponse reallocation,
        StudyStepProgressResponse nextStep,
        boolean curriculumCompleted
) {

    /** 재계산된 남은 학습 시간. 이후 값이 늘어날 수 있어 객체로 감싸 둔다. */
    public record TimeResponse(int remainingStudyMinutes) {
    }

    /**
     * 남은 단계 재조정 결과.
     *
     * @param changed 배정 시간이나 상태가 하나라도 바뀌었으면 true
     * @param steps   재배분 대상이던 단계들의 이전·현재 값 (원래 순서)
     */
    public record ReallocationResponse(boolean changed, List<ReallocatedStepResponse> steps) {
    }

    /**
     * @param previousAllocatedMinutes 재배분 전 배정 시간
     * @param allocatedMinutes         재배분 후 배정 시간 (SKIPPED 는 0)
     * @param status                   재배분 후 상태 (PENDING 또는 SKIPPED)
     */
    public record ReallocatedStepResponse(
            UUID stepId,
            int previousAllocatedMinutes,
            int allocatedMinutes,
            StudyStepStatus status
    ) {

        static ReallocatedStepResponse from(StepChange change) {
            return new ReallocatedStepResponse(
                    change.stepId(),
                    change.previousAllocatedMinutes(),
                    change.allocatedMinutes(),
                    change.status());
        }
    }

    public static StepCompletionResponse from(CompletionResult result) {
        ReallocationOutcome reallocation = result.reallocation();
        return new StepCompletionResponse(
                StudyStepProgressResponse.from(result.completedStep()),
                new TimeResponse(result.remainingStudyMinutes()),
                new ReallocationResponse(
                        reallocation.changed(),
                        reallocation.steps().stream()
                                .map(ReallocatedStepResponse::from)
                                .toList()),
                result.nextStep().map(StudyStepProgressResponse::from).orElse(null),
                result.curriculumCompleted()
        );
    }
}
