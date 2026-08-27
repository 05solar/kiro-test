package com.naeil.study.topic.entity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Topic의 <b>학습 우선순위</b>.
 *
 * <p><b>시험 출제 확률이 아니다.</b> 핵심 개념성, 문서 내 반복도, 다른 개념과의 연결성,
 * 전체 내용을 이해하는 데 필요한 정도로 판단한다.
 * 화면에도 "시험 출제 가능성"이 아니라 "학습 우선순위"로 표시한다.
 *
 * <pre>
 * VERY_HIGH  반드시 우선적으로 학습해야 할 핵심 내용
 * HIGH       높은 우선순위
 * MEDIUM     시간이 있다면 학습해야 하는 내용
 * LOW        시간이 부족하면 줄일 수 있는 세부 내용
 * </pre>
 */
public enum TopicImportance {

    VERY_HIGH,
    HIGH,
    MEDIUM,
    LOW;

    /** AI가 돌려준 문자열을 값으로 바꾼다. 목록에 없으면 비어 있다. */
    public static Optional<TopicImportance> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(importance -> importance.name().equals(normalized))
                .findFirst();
    }
}
