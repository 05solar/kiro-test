package com.naeil.study.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.analysis.client.AiAnalysisClient;
import com.naeil.study.analysis.client.FakeAiAnalysisClient;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicResult;
import com.naeil.study.analysis.exception.AiAnalysisException;
import com.naeil.study.session.entity.SessionStatus;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.repository.StudySessionRepository;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.repository.TopicRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
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
 * 6단계 완료 시나리오를 검증하는 통합 테스트.
 *
 * <pre>
 * 세션 생성 → 시험 정보 → 자료 업로드 → 파싱 → 학습 맥락 → 분석 → Topic 조회
 * </pre>
 *
 * <p><b>실제 AI API를 부르지 않는다.</b> {@link FakeAiAnalysisClient}로 대체한다.
 * 과금이 발생하고 응답이 매번 달라 테스트가 흔들리기 때문이다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@DisplayName("AI 분석 통합 - 자료 분석부터 Topic 조회까지")
class AnalysisApiIntegrationTest {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.local.root-path", () -> storageRoot.toString());
    }

    @TestConfiguration
    static class FakeAiConfig {

        @Bean
        AiAnalysisClient aiAnalysisClient() {
            return new FakeAiAnalysisClient();
        }
    }

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {
            };

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AiAnalysisClient aiAnalysisClient;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private StudySessionRepository studySessionRepository;

    private FakeAiAnalysisClient fakeAi() {
        return (FakeAiAnalysisClient) aiAnalysisClient;
    }

    @BeforeEach
    void resetAi() {
        fakeAi().reset();
    }

    private HttpEntity<String> jsonRequest(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        return new HttpEntity<>(body, headers);
    }

    private ByteArrayResource part(String fileName, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    private String createSession() {
        return (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");
    }

    private void registerExamInfo(String sessionCode) {
        restTemplate.exchange("/api/sessions/" + sessionCode + "/exam", HttpMethod.PUT,
                jsonRequest("""
                        {"subject":"운영체제","examAt":"2030-01-01T10:00:00","availableStudyMinutes":360}
                        """), JSON_MAP);
    }

    private void uploadAndParse(String sessionCode, String fileName, String content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", part(fileName, content));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        restTemplate.exchange("/api/sessions/" + sessionCode + "/documents", HttpMethod.POST,
                new HttpEntity<>(body, headers), JSON_MAP);
        restTemplate.exchange("/api/sessions/" + sessionCode + "/documents/parse",
                HttpMethod.POST, null, JSON_MAP);
    }

    /** 분석 준비를 마친 세션. 자료 하나가 PARSED 상태다. */
    private String readySession() {
        String sessionCode = createSession();
        registerExamInfo(sessionCode);
        uploadAndParse(sessionCode, "운영체제_1주차.txt", """
                1장 프로세스

                운영체제에서 프로세스는 실행 중인 프로그램을 의미한다.
                CPU 스케줄링은 준비 큐에서 실행할 프로세스를 고르는 과정이다.
                """);
        return sessionCode;
    }

    private ResponseEntity<Map<String, Object>> analyze(String sessionCode) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/analysis",
                HttpMethod.POST, null, JSON_MAP);
    }

    private ResponseEntity<Map<String, Object>> topics(String sessionCode) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/topics",
                HttpMethod.GET, null, JSON_MAP);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> topicsOf(ResponseEntity<Map<String, Object>> response) {
        return (List<Map<String, Object>>) response.getBody().get("topics");
    }

    @Test
    @DisplayName("자료를 분석하면 Topic이 저장되고 세션이 READY가 된다")
    void analyzesAndSavesTopics() {
        String sessionCode = readySession();

        ResponseEntity<Map<String, Object>> analyzed = analyze(sessionCode);

        assertThat(analyzed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(analyzed.getBody().get("status")).isEqualTo("READY");
        assertThat(analyzed.getBody().get("topicCount")).isEqualTo(1);

        StudySession session = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.READY);

        List<Topic> saved = topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(session.getId());
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getTitle()).isEqualTo("CPU 스케줄링");
        assertThat(saved.get(0).getKeyPoints()).containsExactly("FCFS", "Round Robin");
        assertThat(saved.get(0).getSourceDocumentIds()).hasSize(1);
    }

    @Test
    @DisplayName("분석 후 Topic 목록을 조회할 수 있다")
    void readsTopicsAfterAnalysis() {
        String sessionCode = readySession();
        analyze(sessionCode);

        ResponseEntity<Map<String, Object>> response = topics(sessionCode);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> topics = topicsOf(response);
        assertThat(topics).hasSize(1);
        assertThat(topics.get(0).get("title")).isEqualTo("CPU 스케줄링");
        assertThat(topics.get(0).get("importance")).isEqualTo("HIGH");
        assertThat(topics.get(0).get("estimatedStudyMinutes")).isEqualTo(30);
        assertThat(topics.get(0).get("pastExamMatched")).isEqualTo(true);
        assertThat(topics.get(0).get("topicOrder")).isEqualTo(1);
    }

    @Test
    @DisplayName("분석 전에는 Topic 목록이 비어 있다")
    void topicsAreEmptyBeforeAnalysis() {
        String sessionCode = readySession();

        assertThat(topicsOf(topics(sessionCode))).isEmpty();
    }

    @Test
    @DisplayName("학습 맥락이 있으면 AI 요청에 담긴다")
    void includesStudyContextInRequest() {
        String sessionCode = readySession();
        restTemplate.exchange("/api/sessions/" + sessionCode + "/study-context", HttpMethod.PUT,
                jsonRequest("""
                        {"professorEmphasis":"교착상태 강조","pastExamInfo":null,
                         "weakAreas":"가상 메모리","mustStudyAreas":"교착상태"}
                        """), JSON_MAP);

        analyze(sessionCode);

        var mergeRequest = fakeAi().mergeRequests().get(fakeAi().mergeRequests().size() - 1);
        assertThat(mergeRequest.studyContext().professorEmphasis()).isEqualTo("교착상태 강조");
        assertThat(mergeRequest.studyContext().weakAreas()).isEqualTo("가상 메모리");
        assertThat(mergeRequest.studyContext().mustStudyAreas()).isEqualTo("교착상태");
        assertThat(mergeRequest.subject()).isEqualTo("운영체제");
    }

    @Test
    @DisplayName("학습 맥락이 없어도 분석은 정상 진행된다")
    void analyzesWithoutStudyContext() {
        String sessionCode = readySession();

        ResponseEntity<Map<String, Object>> analyzed = analyze(sessionCode);

        assertThat(analyzed.getStatusCode()).isEqualTo(HttpStatus.OK);
        var mergeRequest = fakeAi().mergeRequests().get(fakeAi().mergeRequests().size() - 1);
        assertThat(mergeRequest.studyContext().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("PARSE_FAILED 자료는 분석 대상에서 빠진다")
    void excludesFailedDocuments() {
        String sessionCode = createSession();
        registerExamInfo(sessionCode);
        uploadAndParse(sessionCode, "정상.txt", "운영체제에서 프로세스는 실행 중인 프로그램을 의미한다.");
        // 텍스트 레이어가 없는 파일이라 파싱에 실패한다
        uploadAndParse(sessionCode, "스캔본.pdf", "%PDF-1.4 broken");

        ResponseEntity<Map<String, Object>> analyzed = analyze(sessionCode);

        assertThat(analyzed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fakeAi().chunkRequests()).allSatisfy(
                request -> assertThat(request.fileName()).isEqualTo("정상.txt"));
    }

    @Test
    @DisplayName("시험 정보가 없으면 400")
    void returns400WithoutExamInfo() {
        String sessionCode = createSession();
        uploadAndParse(sessionCode, "자료.txt", "운영체제에서 프로세스는 실행 중인 프로그램을 의미한다.");

        ResponseEntity<Map<String, Object>> response = analyze(sessionCode);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("EXAM_INFO_REQUIRED");
    }

    @Test
    @DisplayName("PARSED 자료가 없으면 400")
    void returns400WithoutParsedDocument() {
        String sessionCode = createSession();
        registerExamInfo(sessionCode);

        ResponseEntity<Map<String, Object>> response = analyze(sessionCode);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("NO_PARSED_DOCUMENT");
    }

    @Test
    @DisplayName("AI가 실패하면 502를 반환하고 세션은 ANALYSIS_FAILED가 된다")
    void marksAnalysisFailed() {
        String sessionCode = readySession();
        fakeAi().failMerge(new AiAnalysisException("ai call failed"));

        ResponseEntity<Map<String, Object>> response = analyze(sessionCode);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().get("code")).isEqualTo("ANALYSIS_FAILED");

        StudySession session = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ANALYSIS_FAILED);
        assertThat(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(session.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("분석에 실패해도 자료와 학습 맥락은 남아 있고 다시 분석할 수 있다")
    void retriesAfterFailure() {
        String sessionCode = readySession();
        fakeAi().failMerge(new AiAnalysisException("ai call failed"));
        analyze(sessionCode);

        // AI 쪽 문제가 해소된 뒤 다시 요청한다.
        fakeAi().respondMergeWith(FakeAiAnalysisClient::defaultResult);
        ResponseEntity<Map<String, Object>> retried = analyze(sessionCode);

        assertThat(retried.getStatusCode()).isEqualTo(HttpStatus.OK);
        StudySession session = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.READY);
        assertThat(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(session.getId()))
                .hasSize(1);
    }

    @Test
    @DisplayName("다시 분석하면 기존 Topic을 교체한다")
    void replacesExistingTopics() {
        String sessionCode = readySession();
        analyze(sessionCode);
        StudySession session = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        assertThat(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(session.getId()))
                .hasSize(1);

        fakeAi().respondMergeWith(() -> new AiTopicAnalysisResult(List.of(
                new AiTopicResult("교착상태", "새 요약", List.of("상호배제", "점유와 대기"),
                        "VERY_HIGH", 40, true, false, false, true, List.of("DOC_1")),
                new AiTopicResult("가상 메모리", "새 요약", List.of("페이지 교체"),
                        "MEDIUM", 25, false, false, true, false, List.of("DOC_1")))));
        analyze(sessionCode);

        List<Topic> topics = topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(session.getId());
        assertThat(topics).hasSize(2);
        assertThat(topics).extracting(Topic::getTitle).containsExactly("교착상태", "가상 메모리");
        assertThat(topics.get(0).isMustStudyMatched()).isTrue();
        assertThat(topics.get(1).isWeakAreaMatched()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 세션이면 404")
    void returns404ForUnknownSession() {
        assertThat(analyze("ZZZZZZZZ").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(topics("ZZZZZZZZ").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("다른 세션의 Topic은 보이지 않는다")
    void doesNotLeakTopicsOfOtherSession() {
        String ownerCode = readySession();
        analyze(ownerCode);
        String otherCode = readySession();

        assertThat(topicsOf(topics(otherCode))).isEmpty();
    }

    @Test
    @DisplayName("Topic 예상시간 합이 남은 학습시간을 넘어도 그대로 저장한다")
    void keepsTopicsExceedingRemainingMinutes() {
        String sessionCode = readySession();
        fakeAi().respondMergeWith(() -> new AiTopicAnalysisResult(List.of(
                new AiTopicResult("주제1", "요약", List.of("개념"), "VERY_HIGH", 120,
                        false, false, false, false, List.of("DOC_1")),
                new AiTopicResult("주제2", "요약", List.of("개념"), "VERY_HIGH", 120,
                        false, false, false, false, List.of("DOC_1")),
                new AiTopicResult("주제3", "요약", List.of("개념"), "HIGH", 120,
                        false, false, false, false, List.of("DOC_1")),
                new AiTopicResult("주제4", "요약", List.of("개념"), "MEDIUM", 120,
                        false, false, false, false, List.of("DOC_1")))));

        analyze(sessionCode);

        StudySession session = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        List<Topic> topics = topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(session.getId());
        assertThat(topics).hasSize(4);
        assertThat(topics.stream().mapToInt(Topic::getEstimatedStudyMinutes).sum()).isEqualTo(480);
        assertThat(session.getRemainingStudyMinutes()).isEqualTo(360);
    }
}
