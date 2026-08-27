package com.naeil.study.curriculum.entity;

/**
 * 학습 계획의 진행 상태.
 *
 * <pre>
 * CREATED → IN_PROGRESS → COMPLETED
 * </pre>
 *
 * <p>7단계에서는 {@link #CREATED}만 만들어진다.
 * 나머지 값은 학습 진행 단계(8단계)에서 쓰기 위해 미리 정의해 둔다.
 */
public enum CurriculumStatus {

    CREATED,
    IN_PROGRESS,
    COMPLETED
}
