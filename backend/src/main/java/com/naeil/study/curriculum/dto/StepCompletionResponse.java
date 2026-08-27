package com.naeil.study.curriculum.dto;

import com.naeil.study.curriculum.service.StudyStepService.CompletionResult;

/**
 * 학습 단계 완료 응답.
 *
 * <p>다음 단계를 함께 내려준다. 완료 직후 화면이 무엇을 보여줄지 정하려면 한 번 더
 * 계획을 조회해야 하는데, 그 사이 다른 기기에서 상태가 바뀔 수 있다.
 *
 * <p>다음 단계를 <b>자동으로 시작하지는 않는다.</b> 완료 버튼을 누른 시점과 실제로 다음 공부를
 * 시작하는 시점은 다르고, 그 차이가 실제 학습시간에 그대로 들어가기 때문이다.
 *
 * @param nextStep            다음에 시작할 수 있는 단계. 계획을 다 마쳤으면 null
 * @param curriculumCompleted 계획 전체가 끝났는지. 세션이 끝났다는 뜻은 아니다
 */
public record StepCompletionResponse(
        StudyStepProgressResponse completedStep,
        StudyStepProgressResponse nextStep,
        boolean curriculumCompleted
) {

    public static StepCompletionResponse from(CompletionResult result) {
        return new StepCompletionResponse(
                StudyStepProgressResponse.from(result.completedStep()),
                result.nextStep().map(StudyStepProgressResponse::from).orElse(null),
                result.curriculumCompleted()
        );
    }
}
