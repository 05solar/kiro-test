package com.naeil.study.curriculum.entity;

/**
 * 학습 단계의 진행 상태.
 *
 * <pre>
 * PENDING → IN_PROGRESS → COMPLETED
 *                       ↘ SKIPPED
 * </pre>
 *
 * <p>7단계에서 만들어지는 단계는 모두 {@link #PENDING}이다.
 * 상태를 바꾸는 기능은 학습 진행 단계(8단계)에서 구현한다.
 */
public enum StudyStepStatus {

    PENDING,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED
}
