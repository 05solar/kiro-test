package com.naeil.study.curriculum.planner;

import java.util.List;

/**
 * 동적 재조정 결과.
 *
 * @param steps                입력한 {@code PENDING} 단계 순서대로의 재배정 결과
 * @param totalAllocatedMinutes 재배정한 시간의 합. 항상 남은 학습 시간 이하다
 */
public record ReallocationResult(List<ReallocatedStep> steps, int totalAllocatedMinutes) {
}
