package com.naeil.study.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.document.repository.DocumentRepository;
import com.naeil.study.session.entity.SessionStatus;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.repository.StudySessionRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
 * 3단계 완료 시나리오를 그대로 검증하는 통합 테스트.
 *
 * <pre>
 * 세션 생성 → 시험 정보 등록 → 파일 업로드 → 목록 조회 → 파일 삭제
 * </pre>
 *
 * <p>Storage root를 임시 디렉터리로 지정해 프로젝트 폴더에 파일이 남지 않게 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("강의자료 API 통합 - 업로드/목록/삭제")
class DocumentApiIntegrationTest {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.local.root-path", () -> storageRoot.toString());
    }

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {
            };

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private StudySessionRepository studySessionRepository;

    /** 업로드 파일 한 건. 파일명을 그대로 넘기기 위해 getFilename을 덮어쓴다. */
    private ByteArrayResource part(String fileName, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
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

    @Test
    @DisplayName("세션 생성부터 파일 삭제까지 전체 흐름이 동작한다")
    void fullScenario() {
        String sessionCode = createSessionWithExamInfo();

        // 1. 파일 3개 업로드
        ResponseEntity<Map<String, Object>> uploaded = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/documents",
                HttpMethod.POST,
                uploadRequest(
                        part("운영체제_1주차.pdf", "week1"),
                        part("운영체제_2주차.pdf", "week2 content"),
                        part("운영체제_정리.docx", "summary")),
                JSON_MAP);

        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        List<Map<String, Object>> documents = documentsOf(uploaded);
        assertThat(documents).hasSize(3);
        assertThat(documents).extracting(d -> d.get("originalFileName"))
                .containsExactly("운영체제_1주차.pdf", "운영체제_2주차.pdf", "운영체제_정리.docx");
        assertThat(documents).extracting(d -> d.get("fileType")).containsExactly("PDF", "PDF", "DOCX");
        assertThat(documents).allSatisfy(d -> assertThat(d.get("status")).isEqualTo("UPLOADED"));
        assertThat(documents).allSatisfy(d -> {
            assertThat(d).doesNotContainKey("storagePath");
            assertThat(d).doesNotContainKey("storedFileName");
        });

        // 2. 실제 파일이 세션 경로에 저장되었는지 확인
        StudySession session = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        Path sessionDir = storageRoot.resolve("sessions").resolve(session.getId().toString()).resolve("documents");
        assertThat(sessionDir).exists();
        assertThat(sessionDir.toFile().list()).hasSize(3);

        // 3. 세션 상태가 UPLOADING 으로 바뀐다
        assertThat(session.getStatus()).isEqualTo(SessionStatus.UPLOADING);

        // 4. 목록 조회
        ResponseEntity<Map<String, Object>> listed = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/documents", HttpMethod.GET, null, JSON_MAP);

        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(documentsOf(listed)).hasSize(3);
        assertThat(documentsOf(listed).get(0).get("createdAt")).isNotNull();

        // 5. 파일 하나 삭제
        String documentId = (String) documents.get(0).get("id");
        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/documents/" + documentId,
                HttpMethod.DELETE, null, Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(documentsOf(restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/documents", HttpMethod.GET, null, JSON_MAP))).hasSize(2);
        assertThat(sessionDir.toFile().list()).hasSize(2);
    }

    @Test
    @DisplayName("지원하지 않는 형식이 섞이면 전체 요청이 실패하고 아무 파일도 저장되지 않는다")
    void rejectsWholeRequestWhenOneFileIsUnsupported() {
        String sessionCode = createSessionWithExamInfo();

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/documents",
                HttpMethod.POST,
                uploadRequest(
                        part("정상.pdf", "ok"),
                        part("정상2.txt", "ok"),
                        part("악성.exe", "bad")),
                JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("UNSUPPORTED_FILE_TYPE");

        StudySession session = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        assertThat(documentRepository.countByStudySessionId(session.getId())).isZero();
        Path sessionDir = storageRoot.resolve("sessions").resolve(session.getId().toString());
        assertThat(Files.notExists(sessionDir) || sessionDir.toFile().list().length == 0).isTrue();
        // 업로드가 실패했으므로 상태도 그대로다
        assertThat(session.getStatus()).isEqualTo(SessionStatus.CREATED);
    }

    @Test
    @DisplayName("다른 세션의 문서는 삭제할 수 없다")
    void cannotDeleteDocumentOfAnotherSession() {
        String ownerCode = createSessionWithExamInfo();
        String attackerCode = createSessionWithExamInfo();

        ResponseEntity<Map<String, Object>> uploaded = restTemplate.exchange(
                "/api/sessions/" + ownerCode + "/documents", HttpMethod.POST,
                uploadRequest(part("남의자료.pdf", "secret")), JSON_MAP);
        String documentId = (String) documentsOf(uploaded).get(0).get("id");

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/sessions/" + attackerCode + "/documents/" + documentId,
                HttpMethod.DELETE, null, JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("DOCUMENT_NOT_FOUND");

        // 원래 세션에는 그대로 남아 있다
        assertThat(documentsOf(restTemplate.exchange(
                "/api/sessions/" + ownerCode + "/documents", HttpMethod.GET, null, JSON_MAP))).hasSize(1);
    }

    @Test
    @DisplayName("다른 세션의 문서 목록은 보이지 않는다")
    void listsOnlyOwnDocuments() {
        String firstCode = createSessionWithExamInfo();
        String secondCode = createSessionWithExamInfo();

        restTemplate.exchange("/api/sessions/" + firstCode + "/documents", HttpMethod.POST,
                uploadRequest(part("첫번째.pdf", "a")), JSON_MAP);

        assertThat(documentsOf(restTemplate.exchange(
                "/api/sessions/" + secondCode + "/documents", HttpMethod.GET, null, JSON_MAP))).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 세션에 업로드하면 404")
    void returns404ForUnknownSession() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/sessions/ZZZZZZZZ/documents", HttpMethod.POST,
                uploadRequest(part("자료.pdf", "a")), JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("SESSION_NOT_FOUND");
    }

    @Test
    @DisplayName("빈 파일은 400으로 거부한다")
    void rejectsEmptyFile() {
        String sessionCode = createSessionWithExamInfo();

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/documents", HttpMethod.POST,
                uploadRequest(part("빈파일.pdf", "")), JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("EMPTY_FILE");
    }

    @Test
    @DisplayName("11개를 한 번에 올리면 400으로 거부한다")
    void rejectsTooManyFiles() {
        String sessionCode = createSessionWithExamInfo();
        ByteArrayResource[] files = new ByteArrayResource[11];
        for (int i = 0; i < files.length; i++) {
            files[i] = part("자료" + i + ".pdf", "content");
        }

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/documents", HttpMethod.POST,
                uploadRequest(files), JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("FILE_COUNT_EXCEEDED");
    }

    @Test
    @DisplayName("나눠서 올려도 세션 전체 10개를 넘을 수 없다")
    void rejectsWhenSessionFileCountExceeds() {
        String sessionCode = createSessionWithExamInfo();
        ByteArrayResource[] first = new ByteArrayResource[8];
        for (int i = 0; i < first.length; i++) {
            first[i] = part("자료" + i + ".pdf", "content");
        }
        restTemplate.exchange("/api/sessions/" + sessionCode + "/documents", HttpMethod.POST,
                uploadRequest(first), JSON_MAP);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/documents", HttpMethod.POST,
                uploadRequest(part("추가1.pdf", "a"), part("추가2.pdf", "b"), part("추가3.pdf", "c")),
                JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("FILE_COUNT_EXCEEDED");
    }
}
