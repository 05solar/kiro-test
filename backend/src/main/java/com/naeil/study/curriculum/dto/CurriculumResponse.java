package com.naeil.study.curriculum.dto;

import com.naeil.study.curriculum.entity.Curriculum;
import com.naeil.study.curriculum.entity.CurriculumStatus;
import com.naeil.study.curriculum.entity.StudyStep;
import java.util.List;
import java.util.UUID;

/**
 * 학습 계획 응답. 단계는 {@code order} 오름차순이다.
 *
 * <p>{@code initialRemainingMinutes} 와 {@code totalAllocatedMinutes} 를 함께 내려준다.
 * 둘의 차이가 계획에 쓰지 않고 남긴 시간이다.
 */
public record CurriculumResponse(
        UUID curriculumId,
        int initialRemainingMinutes,
        int totalAllocatedMinutes,
        CurriculumStatus status,
        CurriculumProgressResponse progress,
        List<StudyStepResponse> steps
) {

    public static CurriculumResponse of(Curriculum curriculum, List<StudyStep> steps) {
        return new CurriculumResponse(
                curriculum.getId(),
                curriculum.getInitialRemainingMinutes(),
                curriculum.getTotalAllocatedMinutes(),
                curriculum.getStatus(),
                CurriculumProgressResponse.from(steps),
                steps.stream().map(StudyStepResponse::from).toList()
        );
    }
}
