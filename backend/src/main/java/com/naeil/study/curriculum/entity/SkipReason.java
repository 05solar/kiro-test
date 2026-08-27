package com.naeil.study.curriculum.entity;

/**
 * 단계가 {@link StudyStepStatus#SKIPPED} 가 된 이유.
 *
 * <p>사용자가 직접 건너뛰기를 누른 것과 시스템이 시간 부족으로 제외한 것을 구분하기 위해 둔다.
 * 9단계에서 실제로 쓰는 값은 {@link #TIME_CONSTRAINT} 하나뿐이다.
 *
 * <pre>
 * TIME_CONSTRAINT  동적 재조정 과정에서 남은 시간이 부족해 제외됨 (사용자 의사 아님)
 * </pre>
 *
 * <p>이후 사용자 수동 건너뛰기({@code USER_SKIPPED})나 재계획으로 인한 교체
 * ({@code CURRICULUM_REPLACED}) 같은 값이 필요해지면 여기에 더한다.
 */
public enum SkipReason {

    TIME_CONSTRAINT
}
