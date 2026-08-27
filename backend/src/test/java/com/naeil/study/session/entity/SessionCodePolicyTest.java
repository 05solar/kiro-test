package com.naeil.study.session.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SessionCodePolicy - 세션 코드 형식 규칙")
class SessionCodePolicyTest {

    @ParameterizedTest(name = "유효한 코드: {0}")
    @ValueSource(strings = {"7K2M9QXF", "ABCDEFGH", "23456789", "R7HX83NP", "2MDB8QKF"})
    @DisplayName("8자리이고 허용 문자만 쓰면 유효하다")
    void validCodes(String code) {
        assertThat(SessionCodePolicy.isValid(code)).isTrue();
    }

    @ParameterizedTest(name = "유효하지 않은 코드: {0}")
    @ValueSource(strings = {
            "123",              // 길이 부족
            "7K2M9QXFA",        // 길이 초과
            "abcdefgh",         // 소문자
            "7K2M9QX!",         // 허용하지 않는 기호
            "7K2M9QX ",         // 공백
            "0K2M9QXF",         // 제외 문자 0
            "OK2M9QXF",         // 제외 문자 O
            "1K2M9QXF",         // 제외 문자 1
            "IK2M9QXF"          // 제외 문자 I
    })
    @DisplayName("길이나 문자 집합이 어긋나면 유효하지 않다")
    void invalidCodes(String code) {
        assertThat(SessionCodePolicy.isValid(code)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null과 빈 문자열은 유효하지 않다")
    void nullOrEmptyIsInvalid(String code) {
        assertThat(SessionCodePolicy.isValid(code)).isFalse();
    }

    /**
     * 명세에 명시된 허용 문자열 {@code ABCDEFGHJKLMNPQRSTUVWXYZ23456789} 를 정본으로 삼는다.
     * 이 문자열에는 L이 포함되어 있고, 혼동 대상인 1과 I는 이미 제외되어 있으므로 L은 그대로 둔다.
     */
    @DisplayName("허용 문자 집합은 32자이며 0, O, 1, I를 포함하지 않는다")
    @org.junit.jupiter.api.Test
    void alphabetExcludesConfusingCharacters() {
        assertThat(SessionCodePolicy.ALPHABET).isEqualTo("ABCDEFGHJKLMNPQRSTUVWXYZ23456789");
        assertThat(SessionCodePolicy.ALPHABET).hasSize(32);
        assertThat(SessionCodePolicy.ALPHABET).doesNotContain("0", "O", "1", "I");
        assertThat(SessionCodePolicy.LENGTH).isEqualTo(8);
    }
}
