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
 * <p><b>SKIPPED 도 처리된 단계로 센다.</b> 시간 부족으로 제외된 단계는 더 이상 수행 대상이
 * 아니므로, 진행 흐름상 완료한 것과 같이 "지나간" 단계다. 그래서 진행률은
 * {@code (완료 + 제외) / 전체} 로 센다. 다만 화면에서 둘을 구분할 수 있도록
 * {@code completedSteps} 와 {@code skippedSteps} 를 나눠 담는다.
 *
 * @param completedSteps 실제로 완료한 단계 수
 * @param skippedSteps   시간 부족으로 제외된 단계 수
 * @param percentage     처리된 단계 비율(%). 반올림한 정수다
 */
public record CurriculumProgressResponse(
        int completedSteps, int skippedSteps, int totalSteps, int percentage) {

    public static CurriculumProgressResponse from(List<StudyStep> steps) {
        int total = steps.size();
        if (total == 0) {
            return new CurriculumProgressResponse(0, 0, 0, 0);
        }
        int completed = (int) steps.stream().filter(StudyStep::isCompleted).count();
        int skipped = (int) steps.stream().filter(StudyStep::isSkipped).count();
        int processed = completed + skipped;
        return new CurriculumProgressResponse(
                completed, skipped, total, (int) Math.round(processed * 100.0 / total));
    }
}
