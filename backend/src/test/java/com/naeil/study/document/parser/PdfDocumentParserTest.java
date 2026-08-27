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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PdfDocumentParser - PDF 텍스트 추출")
class PdfDocumentParserTest {

    private final PdfDocumentParser parser = new PdfDocumentParser();

    /** 한글을 그릴 수 있는 시스템 폰트 후보. 없는 환경에서는 한글 PDF 테스트를 건너뛴다. */
    private static final List<String> KOREAN_FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/malgun.ttf",
            "C:/Windows/Fonts/gulim.ttc",
            "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
            "/System/Library/Fonts/AppleSDGothicNeo.ttc"
    );

    /** 지정한 줄들을 담은 PDF를 만든다. 페이지 하나에 한 덩어리씩 넣는다. */
    private byte[] createPdf(List<List<String>> pages, PDFont font) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (List<String> lines : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 14);
                    content.setLeading(18);
                    content.newLineAtOffset(50, 700);
                    for (String line : lines) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] createAsciiPdf(List<List<String>> pages) throws IOException {
        return createPdf(pages, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
    }

    private InputStream stream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    @Test
    @DisplayName("담당 형식은 PDF다")
    void supportsPdf() {
        assertThat(parser.supports()).isEqualTo(DocumentFileType.PDF);
    }

    @Test
    @DisplayName("텍스트 기반 PDF에서 본문을 추출한다")
    void extractsTextFromPdf() throws IOException {
        byte[] pdf = createAsciiPdf(List.of(List.of(
                "Chapter 1 Operating System",
                "A process is a program in execution.")));

        ParsedDocument parsed = parser.parse(stream(pdf));

        assertThat(parsed.text()).contains("Chapter 1 Operating System");
        assertThat(parsed.text()).contains("A process is a program in execution.");
        assertThat(parsed.characterCount()).isGreaterThan(20);
    }

    @Test
    @DisplayName("여러 페이지의 텍스트를 모두 추출한다")
    void extractsTextFromMultiplePages() throws IOException {
        byte[] pdf = createAsciiPdf(List.of(
                List.of("Page one content about processes."),
                List.of("Page two content about threads."),
                List.of("Page three content about scheduling.")));

        ParsedDocument parsed = parser.parse(stream(pdf));

        assertThat(parsed.text()).contains("Page one content about processes.");
        assertThat(parsed.text()).contains("Page two content about threads.");
        assertThat(parsed.text()).contains("Page three content about scheduling.");
    }

    @Test
    @DisplayName("줄바꿈 구조를 유지한다")
    void keepsLineStructure() throws IOException {
        byte[] pdf = createAsciiPdf(List.of(List.of(
                "1. Process",
                "1.1 Definition",
                "1.2 State")));

        ParsedDocument parsed = parser.parse(stream(pdf));

        assertThat(parsed.text().lines().map(String::strip).filter(line -> !line.isEmpty()))
                .containsSubsequence("1. Process", "1.1 Definition", "1.2 State");
    }

    @Test
    @DisplayName("한글이 포함된 PDF에서 한글을 추출한다")
    void extractsKoreanText() throws IOException {
        Path fontPath = KOREAN_FONT_CANDIDATES.stream()
                .map(Path::of)
                .filter(Files::exists)
                .findFirst()
                .orElse(null);
        Assumptions.assumeTrue(fontPath != null, "한글 폰트가 없는 환경이라 건너뜁니다");

        byte[] pdf;
        try (PDDocument fontHolder = new PDDocument()) {
            PDFont font = PDType0Font.load(fontHolder, Files.newInputStream(fontPath), true);
            pdf = createPdfWith(fontHolder, font, "운영체제에서 프로세스는 실행 중인 프로그램을 의미한다.");
        }

        ParsedDocument parsed = parser.parse(stream(pdf));

        assertThat(parsed.text()).contains("운영체제에서 프로세스는 실행 중인 프로그램을 의미한다.");
    }

    /** 폰트를 만든 문서에 그대로 페이지를 넣어야 임베딩이 유지된다. */
    private byte[] createPdfWith(PDDocument document, PDFont font, String line) throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(font, 14);
            content.newLineAtOffset(50, 700);
            content.showText(line);
            content.endText();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("텍스트가 없는 PDF는 빈 문자열을 돌려준다 (상위에서 실패 처리한다)")
    void returnsEmptyTextForPdfWithoutTextLayer() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);

            ParsedDocument parsed = parser.parse(stream(out.toByteArray()));

            assertThat(parsed.text().isBlank()).isTrue();
        }
    }

    @Test
    @DisplayName("손상된 PDF는 DocumentParseFailedException이 발생한다")
    void throwsForCorruptedPdf() {
        byte[] corrupted = "%PDF-1.4 this is not a real pdf".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(stream(corrupted)))
                .isInstanceOf(DocumentParseFailedException.class);
    }

    @Test
    @DisplayName("PDF가 아닌 내용은 DocumentParseFailedException이 발생한다")
    void throwsForNonPdfContent() {
        byte[] notPdf = "그냥 텍스트입니다".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(stream(notPdf)))
                .isInstanceOf(DocumentParseFailedException.class);
    }

    @Test
    @DisplayName("실패 사유에 라이브러리 원문 메시지를 담지 않는다")
    void failureReasonDoesNotLeakLibraryMessage() {
        byte[] corrupted = "not a pdf at all".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(stream(corrupted)))
                .isInstanceOf(DocumentParseFailedException.class)
                .extracting(e -> ((DocumentParseFailedException) e).getReason())
                .isEqualTo("pdf text extraction failed");
    }
}
