package com.naeil.study.wronganswer.entity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 오답 복습 우선순위.
 *
 * <p>{@code LOW}가 없다. 오답 요약에 들어온 것 자체가 이미 복습 대상이라
 * "낮음"이라는 단계가 의미를 갖지 않는다.
 *
 * <p>AI가 오답 수, Topic 중요도, 학습 맥락 일치 여부를 근거로 판단한다.
 * 서버에 별도 점수 공식을 두지 않는다.
 */
public enum ReviewPriority {

    VERY_HIGH,
    HIGH,
    MEDIUM;

    /** AI가 돌려준 문자열을 값으로 바꾼다. 목록에 없으면 비어 있다. */
    public static Optional<ReviewPriority> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(priority -> priority.name().equals(normalized))
                .findFirst();
    }
}
