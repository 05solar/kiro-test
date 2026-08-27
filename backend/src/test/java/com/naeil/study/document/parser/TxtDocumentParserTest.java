package com.naeil.study.document.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.exception.DocumentParseFailedException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TxtDocumentParser - TXT 텍스트 추출")
class TxtDocumentParserTest {

    private final TxtDocumentParser parser = new TxtDocumentParser();

    private InputStream stream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    private InputStream utf8(String text) {
        return stream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("담당 형식은 TXT다")
    void supportsTxt() {
        assertThat(parser.supports()).isEqualTo(DocumentFileType.TXT);
    }

    @Test
    @DisplayName("UTF-8 텍스트를 그대로 읽는다")
    void readsUtf8Text() {
        ParsedDocument parsed = parser.parse(utf8("Operating system basics"));

        assertThat(parsed.text()).isEqualTo("Operating system basics");
    }

    @Test
    @DisplayName("한글 UTF-8 텍스트를 읽는다")
    void readsKoreanUtf8Text() {
        ParsedDocument parsed = parser.parse(utf8("운영체제에서 프로세스는 실행 중인 프로그램을 의미한다."));

        assertThat(parsed.text()).isEqualTo("운영체제에서 프로세스는 실행 중인 프로그램을 의미한다.");
    }

    @Test
    @DisplayName("여러 줄을 그대로 유지한다")
    void keepsMultipleLines() {
        String content = "1장 프로세스\n\n1.1 프로세스란\n실행 중인 프로그램이다.";

        ParsedDocument parsed = parser.parse(utf8(content));

        assertThat(parsed.text()).isEqualTo(content);
        assertThat(parsed.text().lines()).hasSize(4);
    }

    @Test
    @DisplayName("UTF-8 BOM을 제거한다")
    void removesUtf8Bom() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            out.write("운영체제 정리".getBytes(StandardCharsets.UTF_8));

            ParsedDocument parsed = parser.parse(stream(out.toByteArray()));

            assertThat(parsed.text()).isEqualTo("운영체제 정리");
            assertThat(parsed.text().charAt(0)).isEqualTo('운');
        }
    }

    @Test
    @DisplayName("CP949(MS949)로 저장된 한글 파일도 읽는다")
    void readsCp949Text() {
        Charset ms949 = Charset.forName("MS949");
        String content = "운영체제에서 프로세스는 실행 중인 프로그램을 의미한다.";

        ParsedDocument parsed = parser.parse(stream(content.getBytes(ms949)));

        assertThat(parsed.text()).isEqualTo(content);
    }

    @Test
    @DisplayName("UTF-8로도 CP949로도 읽을 수 없으면 실패한다")
    void throwsWhenBothCharsetsFail() {
        // MS949에 정의되지 않은 바이트 구간이면서 UTF-8로도 잘못된 시퀀스
        byte[] invalid = {(byte) 0xFF, (byte) 0xFE, (byte) 0xFD, (byte) 0x81, (byte) 0x40, (byte) 0xFF};

        assertThatThrownBy(() -> parser.parse(stream(invalid)))
                .isInstanceOf(DocumentParseFailedException.class);
    }

    @Test
    @DisplayName("빈 파일은 빈 문자열을 돌려준다 (상위에서 실패 처리한다)")
    void returnsEmptyTextForEmptyFile() {
        ParsedDocument parsed = parser.parse(stream(new byte[0]));

        assertThat(parsed.text()).isEmpty();
    }

    @Test
    @DisplayName("영문과 한글이 섞여도 그대로 읽는다")
    void readsMixedText() {
        String content = "CPU Scheduling 이란 무엇인가?\nRound Robin 방식";

        ParsedDocument parsed = parser.parse(utf8(content));

        assertThat(parsed.text()).isEqualTo(content);
    }
}
