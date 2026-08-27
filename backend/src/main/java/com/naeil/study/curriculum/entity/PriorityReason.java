package com.naeil.study.curriculum.entity;

/**
 * 이 단계가 계획에 포함된 이유.
 *
 * <p>사용자가 "왜 이걸 공부해야 하지"를 이해할 수 있도록 화면에 표시할 근거다.
 * 계산에 쓰는 값이 아니라 설명을 위한 값이다.
 *
 * <pre>
 * CORE_TOPIC          중요도가 VERY_HIGH 또는 HIGH
 * PROFESSOR_EMPHASIS  교수님이 강조했다고 사용자가 밝힌 범위
 * PAST_EXAM           기출/예상 문제와 관련
 * WEAK_AREA           사용자가 자신 없다고 밝힌 범위
 * MUST_STUDY          사용자가 반드시 공부하겠다고 밝힌 범위
 * </pre>
 *
 * <p>한 단계에 여러 이유가 붙을 수 있다.
 */
public enum PriorityReason {

    CORE_TOPIC,
    PROFESSOR_EMPHASIS,
    PAST_EXAM,
    WEAK_AREA,
    MUST_STUDY
}
