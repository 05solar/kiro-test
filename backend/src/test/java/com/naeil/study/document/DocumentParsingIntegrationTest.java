package com.naeil.study.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentStatus;
import com.naeil.study.document.repository.DocumentRepository;
import com.naeil.study.session.entity.SessionStatus;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.repository.StudySessionRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * 4단계 완료 시나리오를 그대로 검증하는 통합 테스트.
 *
 * <pre>
 * 세션 생성 → 시험 정보 → 업로드(UPLOADED) → 파싱 → PARSED + extractedText 저장
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("문서 파싱 통합 - 업로드부터 텍스트 추출까지")
class DocumentParsingIntegrationTest {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.local.root-path", () -> storageRoot.toString());
    }

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {
            };

    private static final String PDF_LINE = "A process is a program in execution.";
    private static final String DOCX_LINE = "운영체제에서 프로세스는 실행 중인 프로그램을 의미한다.";
    private static final String TXT_CONTENT = "1장 프로세스\n\n1.1 프로세스란\n실행 중인 프로그램이다.";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private StudySessionRepository studySessionRepository;

    private byte[] pdfBytes(String line) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                content.newLineAtOffset(50, 700);
                content.showText(line);
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] emptyPdfBytes() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] docxBytes(String line) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText(line);
            document.write(out);
            return out.toByteArray();
        }
    }

    private ByteArrayResource part(String fileName, byte[] content) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    private HttpEntity<MultiValueMap<String, Object>> uploadRequest(ByteArrayResource... files) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (ByteArrayResource file : files) {
            body.add("files", file);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }

    private String createSessionWithExamInfo() {
        String sessionCode = (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        String examBody = """
                {"subject":"운영체제","examAt":"2030-01-01T10:00:00","availableStudyMinutes":360}
                """;
        restTemplate.exchange("/api/sessions/" + sessionCode + "/exam", HttpMethod.PUT,
                new HttpEntity<>(examBody, headers), JSON_MAP);
        return sessionCode;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> documentsOf(ResponseEntity<Map<String, Object>> response) {
        return (List<Map<String, Object>>) response.getBody().get("documents");
    }

    private ResponseEntity<Map<String, Object>> upload(String sessionCode, ByteArrayResource... files) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/documents",
                HttpMethod.POST, uploadRequest(files), JSON_MAP);
    }

    private ResponseEntity<Map<String, Object>> parse(String sessionCode, String documentId) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/documents/" + documentId + "/parse",
                HttpMethod.POST, null, JSON_MAP);
    }

    @Test
    @DisplayName("PDF/DOCX/TXT를 업로드하고 전체 파싱하면 텍스트가 저장된다")
    void parsesAllUploadedDocuments() throws IOException {
        String sessionCode = createSessionWithExamInfo();
        upload(sessionCode,
                part("운영체제_1주차.pdf", pdfBytes(PDF_LINE)),
                part("운영체제_정리.docx", docxBytes(DOCX_LINE)),
                part("메모.txt", TXT_CONTENT.getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<Map<String, Object>> parsed = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/documents/parse", HttpMethod.POST, null, JSON_MAP);

        assertThat(parsed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(documentsOf(parsed)).hasSize(3);
        assertThat(documentsOf(parsed)).allSatisfy(d -> {
            assertThat(d.get("status")).isEqualTo("PARSED");
            assertThat((Integer) d.get("characterCount")).isPositive();
            assertThat(d.get("parsedAt")).isNotNull();
        });

        StudySession session = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        List<Document> documents =
                documentRepository.findAllByStudySessionIdOrderByCreatedAtAsc(session.getId());

        assertThat(documents.get(0).getExtractedText()).contains(PDF_LINE);
        assertThat(documents.get(1).getExtractedText()).contains(DOCX_LINE);
        assertThat(documents.get(2).getExtractedText()).isEqualTo(TXT_CONTENT);
        assertThat(documents).allSatisfy(document -> {
            assertThat(document.getStatus()).isEqualTo(DocumentStatus.PARSED);
            assertThat(document.getCharacterCount()).isEqualTo(document.getExtractedText().length());
            assertThat(document.getParsedAt()).isNotNull();
            assertThat(document.getParseErrorMessage()).isNull();
        });

        // 파싱만으로 세션 상태를 ANALYZING 으로 바꾸지 않는다.
        assertThat(session.getStatus()).isEqualTo(SessionStatus.UPLOADING);
    }

    @Test
    @DisplayName("개별 파싱 후 목록 조회에 characterCount와 parsedAt이 포함된다")
    void listIncludesParsingResult() throws IOException {
        String sessionCode = createSessionWithExamInfo();
        String documentId = (String) documentsOf(
                upload(sessionCode, part("자료.pdf", pdfBytes(PDF_LINE)))).get(0).get("id");

        ResponseEntity<Map<String, Object>> response = parse(sessionCode, documentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("PARSED");

        Map<String, Object> listed = documentsOf(restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/documents", HttpMethod.GET, null, JSON_MAP)).get(0);

        assertThat(listed.get("status")).isEqualTo("PARSED");
        assertThat((Integer) listed.get("characterCount")).isPositive();
        assertThat(listed.get("parsedAt")).isNotNull();
        assertThat(listed).doesNotContainKey("extractedText");
    }

    @Test
    @DisplayName("이미 파싱된 문서를 다시 요청하면 같은 결과를 그대로 돌려준다")
    void reparsingParsedDocumentIsIdempotent() throws IOException {
        String sessionCode = createSessionWithExamInfo();
        String documentId = (String) documentsOf(
                upload(sessionCode, part("자료.pdf", pdfBytes(PDF_LINE)))).get(0).get("id");

        Map<String, Object> first = parse(sessionCode, documentId).getBody();
        // 시각은 응답 직렬화 정밀도가 아니라 DB에 저장된 값끼리 비교한다.
        var parsedAtAfterFirst = documentRepository.findById(UUID.fromString(documentId))
                .orElseThrow().getParsedAt();

        Map<String, Object> second = parse(sessionCode, documentId).getBody();

        assertThat(second.get("status")).isEqualTo("PARSED");
        assertThat(second.get("characterCount")).isEqualTo(first.get("characterCount"));
        assertThat(documentRepository.findById(UUID.fromString(documentId)).orElseThrow().getParsedAt())
                .isEqualTo(parsedAtAfterFirst);
    }

    @Test
    @DisplayName("텍스트 레이어가 없는 PDF는 422로 실패하고 PARSE_FAILED가 된다")
    void failsForPdfWithoutTextLayer() throws IOException {
        String sessionCode = createSessionWithExamInfo();
        String documentId = (String) documentsOf(
                upload(sessionCode, part("스캔본.pdf", emptyPdfBytes()))).get(0).get("id");

        ResponseEntity<Map<String, Object>> response = parse(sessionCode, documentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("code")).isEqualTo("NO_EXTRACTABLE_TEXT");

        Document document = documentRepository.findById(UUID.fromString(documentId)).orElseThrow();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PARSE_FAILED);
        assertThat(document.getExtractedText()).isNull();
        assertThat(document.getParsedAt()).isNull();
        assertThat(document.getParseErrorMessage()).isEqualTo("NO_EXTRACTABLE_TEXT");
    }

    @Test
    @DisplayName("손상된 파일은 422로 실패하고 사유가 기록된다")
    void failsForCorruptedFile() {
        String sessionCode = createSessionWithExamInfo();
        String documentId = (String) documentsOf(upload(sessionCode,
                part("손상.pdf", "%PDF-1.4 broken".getBytes(StandardCharsets.UTF_8)))).get(0).get("id");

        ResponseEntity<Map<String, Object>> response = parse(sessionCode, documentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("code")).isEqualTo("DOCUMENT_PARSE_FAILED");
        assertThat(response.getBody().get("message")).isEqualTo("문서 내용을 읽는 중 오류가 발생했습니다.");

        Document document = documentRepository.findById(UUID.fromString(documentId)).orElseThrow();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PARSE_FAILED);
        assertThat(document.getParseErrorMessage()).isEqualTo("pdf text extraction failed");
    }

    @Test
    @DisplayName("실패한 문서는 다시 요청해 재시도할 수 있다")
    void retriesFailedDocument() throws IOException {
        String sessionCode = createSessionWithExamInfo();
        String documentId = (String) documentsOf(
                upload(sessionCode, part("스캔본.pdf", emptyPdfBytes()))).get(0).get("id");
        parse(sessionCode, documentId);
        assertThat(documentRepository.findById(UUID.fromString(documentId)).orElseThrow().getStatus())
                .isEqualTo(DocumentStatus.PARSE_FAILED);

        // 같은 문서를 다시 요청하면 상태가 PARSING 으로 바뀌었다가 다시 실패한다 (재시도가 막히지 않는다)
        ResponseEntity<Map<String, Object>> retried = parse(sessionCode, documentId);

        assertThat(retried.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(documentRepository.findById(UUID.fromString(documentId)).orElseThrow().getStatus())
                .isEqualTo(DocumentStatus.PARSE_FAILED);
    }

    @Test
    @DisplayName("전체 파싱은 이미 파싱된 문서를 건드리지 않고 실패한 문서도 건너뛴다")
    void bulkParseSkipsAlreadyParsedDocuments() throws IOException {
        String sessionCode = createSessionWithExamInfo();
        List<Map<String, Object>> uploaded = documentsOf(upload(sessionCode,
                part("1주차.pdf", pdfBytes("First document about processes.")),
                part("2주차.pdf", pdfBytes("Second document about threads.")),
                part("3주차.pdf", pdfBytes("Third document about scheduling."))));
        String secondId = (String) uploaded.get(1).get("id");

        // 두 번째 문서만 미리 파싱해 둔다
        parse(sessionCode, secondId);
        var parsedAtBeforeBulk = documentRepository.findById(UUID.fromString(secondId))
                .orElseThrow().getParsedAt();

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/documents/parse", HttpMethod.POST, null, JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(documentsOf(response)).hasSize(3);
        assertThat(documentsOf(response)).allSatisfy(d -> assertThat(d.get("status")).isEqualTo("PARSED"));
        // 미리 파싱해 둔 문서는 다시 읽지 않았으므로 parsedAt 이 그대로다
        assertThat(documentRepository.findById(UUID.fromString(secondId)).orElseThrow().getParsedAt())
                .isEqualTo(parsedAtBeforeBulk);
    }

    @Test
    @DisplayName("다른 세션의 문서는 파싱할 수 없다")
    void cannotParseDocumentOfAnotherSession() throws IOException {
        String ownerCode = createSessionWithExamInfo();
        String attackerCode = createSessionWithExamInfo();
        String documentId = (String) documentsOf(
                upload(ownerCode, part("남의자료.pdf", pdfBytes(PDF_LINE)))).get(0).get("id");

        ResponseEntity<Map<String, Object>> response = parse(attackerCode, documentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("DOCUMENT_NOT_FOUND");

        Document document = documentRepository.findById(UUID.fromString(documentId)).orElseThrow();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
    }

    @Test
    @DisplayName("파싱 요청도 세션 접근으로 보관 기한이 연장된다")
    void parsingRefreshesSessionAccessTime() throws IOException {
        String sessionCode = createSessionWithExamInfo();
        String documentId = (String) documentsOf(
                upload(sessionCode, part("자료.pdf", pdfBytes(PDF_LINE)))).get(0).get("id");
        StudySession before = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        var accessedBefore = before.getLastAccessedAt();

        parse(sessionCode, documentId);

        StudySession after = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        assertThat(after.getLastAccessedAt()).isAfterOrEqualTo(accessedBefore);
        assertThat(after.getExpiresAt()).isEqualTo(after.getLastAccessedAt().plusDays(30));
    }
}
