package com.naeil.study.curriculum.planner;

import java.util.List;

/**
 * 계획 결과.
 *
 * @param steps                순서대로 정렬된 학습 단계
 * @param totalAllocatedMinutes 배정 시간의 합. 항상 가용 시간 이하다
 */
public record CurriculumPlan(List<PlannedStep> steps, int totalAllocatedMinutes) {
}
