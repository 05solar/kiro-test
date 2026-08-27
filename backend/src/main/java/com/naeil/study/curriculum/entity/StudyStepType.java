package com.naeil.study.curriculum.entity;

/**
 * 학습 단계의 종류.
 *
 * <pre>
 * STUDY   Topic 하나를 학습한다
 * REVIEW  여러 Topic을 묶어 마지막에 복습한다. Topic과 직접 연결되지 않는다
 * </pre>
 *
 * <p>Quiz는 이 단계에서 만들지 않는다.
 */
public enum StudyStepType {

    STUDY,
    REVIEW
}
