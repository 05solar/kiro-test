package com.naeil.study.curriculum.dto;

import com.naeil.study.curriculum.entity.StudyStep;
import java.util.List;

/**
 * 계획 진행률.
 *
 * <p>DB에 저장하지 않고 단계 상태에서 매번 센다. 진행률을 따로 저장하면 단계 상태와
 * 어긋날 수 있고, 그때 어느 쪽이 맞는지 판단할 근거가 없다.
 *
 * <p>복습 단계도 전체에 포함한다. 사용자가 화면에서 보는 단계 수와 같아야 한다.
 *
 * @param percentage 완료 비율(%). 반올림한 정수다
 */
public record CurriculumProgressResponse(int completedSteps, int totalSteps, int percentage) {

    public static CurriculumProgressResponse from(List<StudyStep> steps) {
        int total = steps.size();
        if (total == 0) {
            return new CurriculumProgressResponse(0, 0, 0);
        }
        int completed = (int) steps.stream().filter(StudyStep::isCompleted).count();
        return new CurriculumProgressResponse(
                completed, total, (int) Math.round(completed * 100.0 / total));
    }
}
