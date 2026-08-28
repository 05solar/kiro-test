package com.naeil.study.session.entity;

/**
 * 학습 내용이 무엇에 근거했는지.
 *
 * <pre>
 * USER_MATERIAL      사용자가 올린 강의자료에서 뽑았다
 * GENERAL_KNOWLEDGE  자료가 없어 과목명과 시험 범위만으로 만들었다
 * </pre>
 *
 * <p>사용자에게 반드시 알려야 하는 구분이다. 일반 지식으로 만든 계획은
 * 실제 수업 범위와 다를 수 있는데, 그걸 모르고 그대로 믿으면 엉뚱한 것을 공부한다.
 *
 * <p>분석 시점에 한 번 정해지고, 그 세션의 Topic·계획·퀴즈가 모두 이 값을 따른다.
 * 값을 여러 곳에 복사해 두지 않는다 — 어긋나면 어느 쪽이 맞는지 알 수 없다.
 */
public enum StudySourceType {

    USER_MATERIAL,
    GENERAL_KNOWLEDGE;

    /** 실제 자료에 근거했는지. 화면 라벨과 프롬프트 분기에 쓴다. */
    public boolean isGrounded() {
        return this == USER_MATERIAL;
    }
}
