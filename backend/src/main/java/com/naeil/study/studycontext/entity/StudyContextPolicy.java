package com.naeil.study.studycontext.entity;

/**
 * 사용자가 입력한 학습 맥락 텍스트의 처리 규칙.
 *
 * <p>네 항목 모두 자유 입력이라 저장 전에 같은 규칙으로 다듬는다.
 */
public final class StudyContextPolicy {

    /** 각 항목의 최대 길이. 무제한 입력을 허용하지 않는다. */
    public static final int MAX_FIELD_LENGTH = 2000;

    private StudyContextPolicy() {
    }

    /**
     * 입력값을 저장 형태로 다듬는다.
     *
     * <p>앞뒤 공백을 지우고, 공백만 남는 값은 {@code null}로 만든다.
     * 빈 문자열과 {@code null}이 섞여 있으면 "입력하지 않음"을 판단하는 조건이
     * 여기저기서 달라진다. 저장 시점에 하나로 통일해 둔다.
     *
     * @return 다듬은 문자열. 의미 있는 글자가 없으면 null
     */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
