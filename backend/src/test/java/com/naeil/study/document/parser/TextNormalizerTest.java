package com.naeil.study.document.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TextNormalizer - 추출 텍스트 정규화")
class TextNormalizerTest {

    private final TextNormalizer normalizer = new TextNormalizer();

    @Test
    @DisplayName("CRLF와 CR을 LF로 통일한다")
    void unifiesLineSeparators() {
        String text = "첫째 줄\r\n둘째 줄\r셋째 줄\n넷째 줄";

        assertThat(normalizer.normalize(text)).isEqualTo("첫째 줄\n둘째 줄\n셋째 줄\n넷째 줄");
    }

    @Test
    @DisplayName("세 줄 이상 이어진 빈 줄을 하나로 줄인다")
    void collapsesExcessiveBlankLines() {
        String text = "내용\n\n\n\n\n다음내용";

        assertThat(normalizer.normalize(text)).isEqualTo("내용\n\n다음내용");
    }

    @Test
    @DisplayName("문단 사이 빈 줄 하나는 그대로 둔다")
    void keepsSingleBlankLine() {
        String text = "1장 프로세스\n\n1.1 프로세스란";

        assertThat(normalizer.normalize(text)).isEqualTo("1장 프로세스\n\n1.1 프로세스란");
    }

    @Test
    @DisplayName("줄 끝 공백을 제거한다")
    void stripsTrailingWhitespacePerLine() {
        String text = "첫째 줄   \n둘째 줄\t\t\n셋째 줄";

        assertThat(normalizer.normalize(text)).isEqualTo("첫째 줄\n둘째 줄\n셋째 줄");
    }

    @Test
    @DisplayName("줄 앞 들여쓰기는 남긴다 (목록 구조 정보다)")
    void keepsLeadingIndentation() {
        String text = "1장 프로세스\n    1.1 프로세스란\n        정의";

        assertThat(normalizer.normalize(text)).isEqualTo("1장 프로세스\n    1.1 프로세스란\n        정의");
    }

    @Test
    @DisplayName("줄 안의 연속 공백은 건드리지 않는다")
    void keepsInnerSpacing() {
        String text = "CPU    Scheduling";

        assertThat(normalizer.normalize(text)).isEqualTo("CPU    Scheduling");
    }

    @Test
    @DisplayName("줄바꿈을 없애 한 줄로 뭉개지 않는다")
    void doesNotFlattenStructure() {
        String text = "1장 프로세스\n\n1.1 프로세스란\n실행 중인 프로그램\n\n1.2 스레드란\n실행 흐름";

        String normalized = normalizer.normalize(text);

        assertThat(normalized.lines()).contains("1장 프로세스", "1.1 프로세스란", "1.2 스레드란");
        assertThat(normalized).doesNotContain("1장 프로세스 1.1 프로세스란");
    }

    @Test
    @DisplayName("줄바꿈 없는 공백(U+00A0)을 일반 공백으로 바꾼다")
    void replacesNonBreakingSpace() {
        String nbsp = String.valueOf((char) 0x00A0);
        String text = "CPU" + nbsp + "Scheduling";

        String normalized = normalizer.normalize(text);

        assertThat(normalized).isEqualTo("CPU Scheduling");
        assertThat(normalized).doesNotContain(nbsp);
    }

    @Test
    @DisplayName("폭 없는 문자(BOM, ZWSP 등)를 제거한다")
    void removesZeroWidthCharacters() {
        String text = (char) 0xFEFF + "운영" + (char) 0x200B + "체제" + (char) 0x200C + " 개요";

        assertThat(normalizer.normalize(text)).isEqualTo("운영체제 개요");
    }

    @Test
    @DisplayName("앞뒤 공백과 빈 줄을 제거한다")
    void stripsSurroundingWhitespace() {
        String text = "\n\n   운영체제 개요   \n\n";

        assertThat(normalizer.normalize(text)).isEqualTo("운영체제 개요");
    }

    @Test
    @DisplayName("null과 빈 문자열은 빈 문자열이 된다")
    void handlesNullAndEmpty() {
        assertThat(normalizer.normalize(null)).isEmpty();
        assertThat(normalizer.normalize("")).isEmpty();
        assertThat(normalizer.normalize("   \n\n   ")).isEmpty();
    }

    @Test
    @DisplayName("표 형태의 탭 구분은 유지한다")
    void keepsTabsInsideLines() {
        String text = "알고리즘\t특징\nFCFS\t선입선출";

        assertThat(normalizer.normalize(text)).isEqualTo("알고리즘\t특징\nFCFS\t선입선출");
    }
}
