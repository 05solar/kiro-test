package com.naeil.study.document.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.exception.DocumentParseFailedException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DocxDocumentParser - DOCX 텍스트 추출")
class DocxDocumentParserTest {

    private final DocxDocumentParser parser = new DocxDocumentParser();

    private byte[] createDocx(Consumer<XWPFDocument> writer) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writer.accept(document);
            document.write(out);
            return out.toByteArray();
        }
    }

    private void addParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText(text);
    }

    private void addTable(XWPFDocument document, List<List<String>> rows) {
        XWPFTable table = document.createTable(rows.size(), rows.get(0).size());
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < rows.get(r).size(); c++) {
                table.getRow(r).getCell(c).setText(rows.get(r).get(c));
            }
        }
    }

    private InputStream stream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    @Test
    @DisplayName("담당 형식은 DOCX다")
    void supportsDocx() {
        assertThat(parser.supports()).isEqualTo(DocumentFileType.DOCX);
    }

    @Test
    @DisplayName("본문 문단의 텍스트를 추출한다")
    void extractsParagraphText() throws IOException {
        byte[] docx = createDocx(document -> {
            addParagraph(document, "1장 운영체제 개요");
            addParagraph(document, "운영체제에서 프로세스는 실행 중인 프로그램을 의미한다.");
        });

        ParsedDocument parsed = parser.parse(stream(docx));

        assertThat(parsed.text()).contains("1장 운영체제 개요");
        assertThat(parsed.text()).contains("운영체제에서 프로세스는 실행 중인 프로그램을 의미한다.");
    }

    @Test
    @DisplayName("표 안의 텍스트도 빠뜨리지 않는다")
    void extractsTableText() throws IOException {
        byte[] docx = createDocx(document -> {
            addParagraph(document, "스케줄링 알고리즘 비교");
            addTable(document, List.of(
                    List.of("알고리즘", "특징"),
                    List.of("FCFS", "먼저 도착한 순서대로 실행"),
                    List.of("Round Robin", "타임 퀀텀 기준 순환")));
        });

        ParsedDocument parsed = parser.parse(stream(docx));

        assertThat(parsed.text()).contains("스케줄링 알고리즘 비교");
        assertThat(parsed.text()).contains("FCFS");
        assertThat(parsed.text()).contains("먼저 도착한 순서대로 실행");
        assertThat(parsed.text()).contains("Round Robin");
        assertThat(parsed.text()).contains("타임 퀀텀 기준 순환");
    }

    @Test
    @DisplayName("표의 셀은 탭으로, 행은 줄바꿈으로 구분한다")
    void separatesTableCellsAndRows() throws IOException {
        byte[] docx = createDocx(document -> addTable(document, List.of(
                List.of("알고리즘", "특징"),
                List.of("FCFS", "선입선출"))));

        ParsedDocument parsed = parser.parse(stream(docx));

        assertThat(parsed.text()).contains("알고리즘\t특징");
        assertThat(parsed.text()).contains("FCFS\t선입선출");
    }

    @Test
    @DisplayName("문단과 표의 등장 순서를 유지한다")
    void keepsDocumentOrder() throws IOException {
        byte[] docx = createDocx(document -> {
            addParagraph(document, "먼저 나오는 문단");
            addTable(document, List.of(List.of("표 내용")));
            addParagraph(document, "나중에 나오는 문단");
        });

        ParsedDocument parsed = parser.parse(stream(docx));

        assertThat(parsed.text().indexOf("먼저 나오는 문단"))
                .isLessThan(parsed.text().indexOf("표 내용"));
        assertThat(parsed.text().indexOf("표 내용"))
                .isLessThan(parsed.text().indexOf("나중에 나오는 문단"));
    }

    @Test
    @DisplayName("빈 문단은 결과에 넣지 않는다")
    void skipsBlankParagraphs() throws IOException {
        byte[] docx = createDocx(document -> {
            addParagraph(document, "내용");
            addParagraph(document, "   ");
            addParagraph(document, "다음 내용");
        });

        ParsedDocument parsed = parser.parse(stream(docx));

        assertThat(parsed.text()).isEqualTo("내용\n다음 내용\n");
    }

    @Test
    @DisplayName("빈 DOCX는 빈 문자열을 돌려준다")
    void returnsEmptyTextForEmptyDocx() throws IOException {
        byte[] docx = createDocx(document -> {
        });

        ParsedDocument parsed = parser.parse(stream(docx));

        assertThat(parsed.text()).isEmpty();
    }

    @Test
    @DisplayName("손상된 DOCX는 DocumentParseFailedException이 발생한다")
    void throwsForCorruptedDocx() {
        byte[] corrupted = "PK this is not a real docx".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(stream(corrupted)))
                .isInstanceOf(DocumentParseFailedException.class)
                .extracting(e -> ((DocumentParseFailedException) e).getReason())
                .isEqualTo("docx text extraction failed");
    }
}
