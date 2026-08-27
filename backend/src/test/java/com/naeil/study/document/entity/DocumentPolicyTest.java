package com.naeil.study.document.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("DocumentPolicy - 파일명 처리 규칙")
class DocumentPolicyTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "운영체제_1주차.pdf, 운영체제_1주차.pdf",
            "'  정리.docx  ', 정리.docx",
            "notes.txt, notes.txt"
    })
    @DisplayName("정상 파일명은 그대로 유지한다")
    void keepsNormalFileName(String input, String expected) {
        assertThat(DocumentPolicy.normalizeFileName(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "../../../etc/passwd.txt, passwd.txt",
            "'..\\..\\test.pdf', test.pdf",
            "/tmp/a/b/c.pdf, c.pdf",
            "'C:\\Users\\me\\운영체제.pdf', 운영체제.pdf"
    })
    @DisplayName("경로가 섞인 파일명에서 마지막 조각만 남긴다")
    void stripsPathFromFileName(String input, String expected) {
        assertThat(DocumentPolicy.normalizeFileName(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", ".", "...", "   "})
    @DisplayName("점이나 공백만 있는 이름은 빈 문자열이 된다")
    void returnsEmptyForMeaninglessName(String input) {
        assertThat(DocumentPolicy.normalizeFileName(input)).isEmpty();
    }

    @Test
    @DisplayName("null 파일명은 빈 문자열이 된다")
    void returnsEmptyForNull() {
        assertThat(DocumentPolicy.normalizeFileName(null)).isEmpty();
    }

    @Test
    @DisplayName("제어문자는 제거한다")
    void removesControlCharacters() {
        assertThat(DocumentPolicy.normalizeFileName("a\u0000b\nc.pdf")).isEqualTo("abc.pdf");
    }

    @Test
    @DisplayName("255자를 넘는 파일명은 잘라낸다")
    void truncatesLongFileName() {
        String longName = "가".repeat(300) + ".pdf";

        assertThat(DocumentPolicy.normalizeFileName(longName)).hasSize(255);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "운영체제.pdf, pdf",
            "정리.DOCX, docx",
            "notes.TxT, txt",
            "archive.tar.gz, gz",
            "../../a.pdf, pdf"
    })
    @DisplayName("확장자는 소문자로 추출한다")
    void extractsExtension(String input, String expected) {
        assertThat(DocumentPolicy.extractExtension(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"noextension", "trailingdot.", ".."})
    @DisplayName("확장자가 없으면 빈 문자열을 돌려준다")
    void returnsEmptyExtension(String input) {
        assertThat(DocumentPolicy.extractExtension(input)).isEmpty();
    }

    @Test
    @DisplayName("업로드 제한 값은 명세와 일치한다")
    void limitsMatchSpecification() {
        assertThat(DocumentPolicy.MAX_FILE_COUNT).isEqualTo(10);
        assertThat(DocumentPolicy.MAX_FILE_SIZE_BYTES).isEqualTo(20L * 1024 * 1024);
        assertThat(DocumentPolicy.MAX_TOTAL_SIZE_BYTES).isEqualTo(100L * 1024 * 1024);
    }
}
