package com.naeil.study.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.analysis.client.AiAnalysisClient;
import com.naeil.study.analysis.client.FakeAiAnalysisClient;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicResult;
import com.naeil.study.chat.client.AiChatClient;
import com.naeil.study.chat.client.FakeAiChatClient;
import com.naeil.study.chat.client.dto.AiChatAnswer;
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
 * 학습 챗봇을 실제 HTTP + DB 로 확인한다.
 *
 * <p>단위 테스트가 잡지 못하는 것을 본다 — 대화가 실제로 저장되어 다시 읽히는지, 긴 한글
 * 답변이 깨지지 않고 왕복하는지(4단계의 {@code @Lob} 사고와 같은 종류), 다른 세션의 코드로는
 * 아무것도 볼 수 없는지.
 *
 * <p>AI는 가짜 구현을 쓴다. 실제 API를 부르지 않는다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@DisplayName("챗봇 API 통합 - 질문부터 대화 복구까지")
class ChatApiIntegrationTest {

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

        @Bean
        AiChatClient aiChatClient() {
            return new FakeAiChatClient();
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
    private AiChatClient aiChatClient;

    private FakeAiAnalysisClient fakeAnalysis() {
        return (FakeAiAnalysisClient) aiAnalysisClient;
    }

    private FakeAiChatClient fakeChat() {
        return (FakeAiChatClient) aiChatClient;
    }

    @BeforeEach
    void resetAi() {
        fakeAnalysis().reset();
        fakeChat().reset();
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

    private String newSession() {
        return (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");
    }

    private void registerExam(String sessionCode, String examScope) {
        restTemplate.exchange("/api/sessions/" + sessionCode + "/exam", HttpMethod.PUT,
                jsonRequest("""
                        {"subject":"운영체제","examScope":"%s",\
                        "examAt":"2030-01-01T10:00:00","availableStudyMinutes":180}
                        """.formatted(examScope)), JSON_MAP);
    }

    /** 분석까지 마친 자료 기반 세션. */
    private String groundedSession() {
        fakeAnalysis().respondMergeWith(() -> new AiTopicAnalysisResult(List.of(
                new AiTopicResult("CPU 스케줄링", "요약", List.of("Round Robin"), "HIGH", 40,
                        false, false, false, false, List.of("DOC_1")))));

        String sessionCode = newSession();
        registerExam(sessionCode, "3장 프로세스");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", part("운영체제.txt",
                "CPU 스케줄링은 준비 큐를 다룬다. Round Robin 은 시간 할당량을 사용하는 선점형 방식이다."));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        restTemplate.exchange("/api/sessions/" + sessionCode + "/documents", HttpMethod.POST,
                new HttpEntity<>(body, headers), JSON_MAP);
        restTemplate.exchange("/api/sessions/" + sessionCode + "/documents/parse",
                HttpMethod.POST, null, JSON_MAP);
        restTemplate.exchange("/api/sessions/" + sessionCode + "/analysis",
                HttpMethod.POST, null, JSON_MAP);
        return sessionCode;
    }

    private ResponseEntity<Map<String, Object>> ask(String sessionCode, String message) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/chat", HttpMethod.POST,
                jsonRequest("{\"message\":\"%s\"}".formatted(message)), JSON_MAP);
    }

    private ResponseEntity<Map<String, Object>> history(String sessionCode) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/chat",
                HttpMethod.GET, null, JSON_MAP);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> messages(ResponseEntity<Map<String, Object>> response) {
        return (List<Map<String, Object>>) response.getBody().get("messages");
    }

    @Test
    @DisplayName("질문하면 답이 오고, 그 대화가 그대로 다시 읽힌다")
    void asksAndRestoresConversation() {
        String sessionCode = groundedSession();

        ResponseEntity<Map<String, Object>> answer = ask(sessionCode, "Round Robin 이 뭐야?");

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(answer.getBody().get("answer")).isEqualTo("가짜 답변입니다.");
        assertThat(answer.getBody().get("grounded")).isEqualTo(true);

        // 다른 기기에서 8자리 코드만으로 들어와도 나눈 대화가 그대로 이어져야 한다.
        ResponseEntity<Map<String, Object>> history = history(sessionCode);

        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody().get("grounded")).isEqualTo(true);
        assertThat(messages(history)).hasSize(2);
        assertThat(messages(history).get(0).get("role")).isEqualTo("USER");
        assertThat(messages(history).get(0).get("content")).isEqualTo("Round Robin 이 뭐야?");
        assertThat(messages(history).get(1).get("role")).isEqualTo("ASSISTANT");
    }

    @Test
    @DisplayName("긴 한글 답변이 깨지지 않고 저장·조회된다")
    void storesLongKoreanAnswer() {
        String sessionCode = groundedSession();
        String longAnswer = "선점형 스케줄링은 실행 중인 프로세스를 강제로 내보낼 수 있다. ".repeat(60);
        fakeChat().respondWith(request -> new AiChatAnswer(longAnswer, true));

        ask(sessionCode, "선점형이 뭐야?");

        List<Map<String, Object>> messages = messages(history(sessionCode));
        assertThat(messages.get(1).get("content")).isEqualTo(longAnswer.strip());
    }

    @Test
    @DisplayName("지난 대화가 다음 질문의 프롬프트에 오래된 것부터 담긴다")
    void carriesConversationIntoNextPrompt() {
        String sessionCode = groundedSession();

        ask(sessionCode, "첫 질문");
        ask(sessionCode, "두 번째 질문");

        assertThat(fakeChat().callCount()).isEqualTo(2);
        assertThat(fakeChat().lastRequest().history()).extracting("content")
                .containsExactly("첫 질문", "가짜 답변입니다.");
        assertThat(fakeChat().lastRequest().question()).isEqualTo("두 번째 질문");
    }

    @Test
    @DisplayName("자료가 없는 세션도 답한다. 다만 자료 기반이라고 하지 않는다")
    void answersWithoutMaterial() {
        fakeAnalysis().respondMergeWith(() -> new AiTopicAnalysisResult(List.of(
                new AiTopicResult("프로세스", "요약", List.of("PCB"), "HIGH", 40,
                        false, false, false, false, List.of()))));

        String sessionCode = newSession();
        registerExam(sessionCode, "3장 프로세스");
        restTemplate.exchange("/api/sessions/" + sessionCode + "/analysis",
                HttpMethod.POST, null, JSON_MAP);

        ResponseEntity<Map<String, Object>> answer = ask(sessionCode, "프로세스가 뭐야?");

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(answer.getBody().get("grounded")).isEqualTo(false);
        // 가짜 클라이언트가 true 로 답해도 자료가 없으면 자료 기반이 될 수 없다.
        assertThat(answer.getBody().get("answeredFromMaterial")).isEqualTo(false);
    }

    @Test
    @DisplayName("분석 전 세션에 질문하면 409. AI 를 부르지 않는다")
    void rejectsBeforeAnalysis() {
        String sessionCode = newSession();
        registerExam(sessionCode, "3장 프로세스");

        ResponseEntity<Map<String, Object>> answer = ask(sessionCode, "프로세스가 뭐야?");

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(answer.getBody().get("code")).isEqualTo("CHAT_NOT_READY");
        assertThat(fakeChat().callCount()).isZero();
    }

    @Test
    @DisplayName("다른 세션의 대화는 볼 수 없다 — 없는 코드와 같은 응답이다")
    void cannotSeeAnotherSessionConversation() {
        String owner = groundedSession();
        ask(owner, "내 질문");

        String stranger = groundedSession();

        // 남의 세션 대화가 내 조회에 섞이지 않는다.
        assertThat(messages(history(stranger))).isEmpty();

        // 존재하지 않는 코드도 같은 404 다. 있는지 없는지 알려주지 않는다.
        ResponseEntity<Map<String, Object>> unknown = history("ZZZZZZZZ");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("code")).isEqualTo("SESSION_NOT_FOUND");
    }
}
