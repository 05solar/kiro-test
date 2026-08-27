package com.naeil.study.studycontext.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("StudyContextPolicy - 학습 맥락 입력 정규화")
class StudyContextPolicyTest {

    @Test
    @DisplayName("앞뒤 공백을 제거한다")
    void stripsSurroundingWhitespace() {
        assertThat(StudyContextPolicy.normalize("   교착상태를 강조함   ")).isEqualTo("교착상태를 강조함");
    }

    @ParameterizedTest(name = "[{0}] → null")
    @ValueSource(strings = {"", " ", "     ", "\t", "\n", "  \n\t  "})
    @DisplayName("공백만 있는 값은 null이 된다")
    void turnsBlankIntoNull(String value) {
        assertThat(StudyContextPolicy.normalize(value)).isNull();
    }

    @Test
    @DisplayName("null은 그대로 null이다")
    void keepsNull() {
        assertThat(StudyContextPolicy.normalize(null)).isNull();
    }

    @Test
    @DisplayName("가운데 줄바꿈과 공백은 그대로 둔다")
    void keepsInnerWhitespace() {
        String value = "교착상태 4가지 조건\n- 상호배제\n- 점유와 대기";

        assertThat(StudyContextPolicy.normalize(value)).isEqualTo(value);
    }

    @Test
    @DisplayName("최대 길이 제한은 2000자다")
    void maxLengthIsTwoThousand() {
        assertThat(StudyContextPolicy.MAX_FIELD_LENGTH).isEqualTo(2000);
    }
}
