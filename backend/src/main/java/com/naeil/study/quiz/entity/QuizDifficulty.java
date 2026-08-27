package com.naeil.study.quiz.entity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 문제 난이도.
 *
 * <p>AI가 문제를 만들면서 함께 정한다. 서버는 값의 유효성만 확인하고 분포를 강제하지 않는다.
 * 기본 분포(EASY 1 / MEDIUM 2~3 / HARD 1)는 프롬프트에서 권고한다.
 */
public enum QuizDifficulty {

    EASY,
    MEDIUM,
    HARD;

    /** AI가 돌려준 문자열을 값으로 바꾼다. 목록에 없으면 비어 있다. */
    public static Optional<QuizDifficulty> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(difficulty -> difficulty.name().equals(normalized))
                .findFirst();
    }
}
