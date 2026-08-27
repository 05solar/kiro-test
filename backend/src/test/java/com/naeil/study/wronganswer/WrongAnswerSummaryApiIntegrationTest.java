package com.naeil.study.wronganswer;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.analysis.client.AiAnalysisClient;
import com.naeil.study.analysis.client.FakeAiAnalysisClient;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicResult;
import com.naeil.study.quiz.client.AiQuizClient;
import com.naeil.study.quiz.client.FakeAiQuizClient;
import com.naeil.study.wronganswer.client.AiWrongAnswerSummaryClient;
import com.naeil.study.wronganswer.client.FakeAiWrongAnswerSummaryClient;
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
 * 오답 복습 요약 시나리오를 검증하는 통합 테스트.
 *
 * <pre>
 * 학습 완료 → 퀴즈 → 채점 → 오답 요약 생성 → 조회 (jsonb 왕복 확인)
 * </pre>
 *
 * <p>AI 세 가지(분석 / 퀴즈 / 요약) 모두 가짜 구현을 쓴다. 실제 API 를 부르지 않는다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@DisplayName("오답 복습 요약 API 통합 - 채점 결과부터 맞춤 요약까지")
class WrongAnswerSummaryApiIntegrationTest {

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
        AiQuizClient aiQuizClient() {
            return new FakeAiQuizClient();
        }

        @Bean
        AiWrongAnswerSummaryClient aiWrongAnswerSummaryClient() {
            return new FakeAiWrongAnswerSummaryClient();
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
    private AiQuizClient aiQuizClient;

    @Autowired
    private AiWrongAnswerSummaryClient aiSummaryClient;

    private FakeAiAnalysisClient fakeAnalysis() {
        return (FakeAiAnalysisClient) aiAnalysisClient;
    }

    private FakeAiQuizClient fakeQuiz() {
        return (FakeAiQuizClient) aiQuizClient;
    }

    private FakeAiWrongAnswerSummaryClient fakeSummary() {
        return (FakeAiWrongAnswerSummaryClient) aiSummaryClient;
    }

    @BeforeEach
    void resetAi() {
        fakeAnalysis().reset();
        fakeQuiz().reset();
        fakeSummary().reset();
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

    private String plannedSession() {
        fakeAnalysis().respondMergeWith(() -> new AiTopicAnalysisResult(List.of(
                new AiTopicResult("CPU 스케줄링", "요약", List.of("Round Robin"), "VERY_HIGH", 50,
                        false, false, false, false, List.of("DOC_1")))));

        String sessionCode = (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");

        restTemplate.exchange("/api/sessions/" + sessionCode + "/exam", HttpMethod.PUT,
                jsonRequest("""
                        {"subject":"운영체제","examAt":"2030-01-01T10:00:00","availableStudyMinutes":180}
                        """), JSON_MAP);

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
        restTemplate.exchange("/api/sessions/" + sessionCode + "/curriculum",
                HttpMethod.POST, null, JSON_MAP);
        return sessionCode;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> steps(String sessionCode) {
        return (List<Map<String, Object>>) restTemplate
                .exchange("/api/sessions/" + sessionCode + "/curriculum", HttpMethod.GET, null, JSON_MAP)
                .getBody().get("steps");
    }

    /** 첫 단계를 완료하고 퀴즈까지 만든 뒤 문제 목록을 돌려준다. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> preparedQuizzes(String sessionCode) {
        Map<String, Object> first = steps(sessionCode).get(0);
        Object stepId = first.get("id");
        restTemplate.exchange("/api/sessions/" + sessionCode + "/steps/" + stepId + "/start",
                HttpMethod.POST, null, JSON_MAP);
        restTemplate.exchange("/api/sessions/" + sessionCode + "/steps/" + stepId + "/complete",
                HttpMethod.POST, null, JSON_MAP);
        String topicId = String.valueOf(first.get("topicId"));
        ResponseEntity<Map<String, Object>> generated = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/topics/" + topicId + "/quizzes",
                HttpMethod.POST, null, JSON_MAP);
        return (List<Map<String, Object>>) generated.getBody().get("quizzes");
    }

    private void answer(String sessionCode, Object quizId, int selectedIndex) {
        restTemplate.exchange("/api/sessions/" + sessionCode + "/quizzes/" + quizId + "/answer",
                HttpMethod.POST, jsonRequest("{\"selectedIndex\":" + selectedIndex + "}"), JSON_MAP);
    }

    /** Fake 정답 [0,1,2,3,0] 기준으로 wrongCount 개만 틀리게 답한다. */
    private void answerAll(String sessionCode, List<Map<String, Object>> quizzes, int wrongCount) {
        int[] correct = {0, 1, 2, 3, 0};
        for (int i = 0; i < quizzes.size(); i++) {
            boolean makeWrong = i < wrongCount;
            int selected = makeWrong ? (correct[i] + 1) % 4 : correct[i];
            answer(sessionCode, quizzes.get(i).get("id"), selected);
        }
    }

    private ResponseEntity<Map<String, Object>> generateSummary(String sessionCode) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/wrong-answer-summary",
                HttpMethod.POST, null, JSON_MAP);
    }

    private ResponseEntity<Map<String, Object>> findSummary(String sessionCode) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/wrong-answer-summary",
                HttpMethod.GET, null, JSON_MAP);
    }

    @Test
    @DisplayName("오답이 있으면 Topic 별 복습 요약이 생성되고 조회로 복구된다")
    void generatesSummaryFromWrongAnswers() {
        String sessionCode = plannedSession();
        List<Map<String, Object>> quizzes = preparedQuizzes(sessionCode);
        answerAll(sessionCode, quizzes, 2);

        ResponseEntity<Map<String, Object>> created = generateSummary(sessionCode);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("hasWrongAnswers")).isEqualTo(true);
        assertThat(created.getBody().get("wrongAnswerCount")).isEqualTo(2);
        assertThat(created.getBody().get("overallSummary")).isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topics = (List<Map<String, Object>>) created.getBody().get("topics");
        assertThat(topics).hasSize(1);
        assertThat(topics.get(0).get("topicTitle")).isEqualTo("CPU 스케줄링");
        assertThat(topics.get(0).get("priority")).isEqualTo("HIGH");
        assertThat((List<?>) topics.get(0).get("wrongConcepts")).isNotEmpty();
        assertThat((List<?>) topics.get(0).get("keyReviewPoints")).isNotEmpty();

        // AI 요청에는 오답 2건만 담긴다
        assertThat(fakeSummary().requests().get(0).topics().get(0).wrongAnswers()).hasSize(2);

        // 재접속: 조회로 같은 내용이 복구된다 (jsonb 왕복)
        ResponseEntity<Map<String, Object>> found = findSummary(sessionCode);
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().get("wrongAnswerCount")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> foundTopics = (List<Map<String, Object>>) found.getBody().get("topics");
        assertThat(foundTopics.get(0).get("summary")).isEqualTo(topics.get(0).get("summary"));
    }

    @Test
    @DisplayName("답안이 그대로면 다시 요청해도 AI를 부르지 않는다")
    void reusesCachedSummary() {
        String sessionCode = plannedSession();
        List<Map<String, Object>> quizzes = preparedQuizzes(sessionCode);
        answerAll(sessionCode, quizzes, 1);
        generateSummary(sessionCode);

        ResponseEntity<Map<String, Object>> again = generateSummary(sessionCode);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fakeSummary().callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("전부 맞혔으면 AI 호출 없이 오답 없음으로 응답한다")
    void reportsNoWrongAnswers() {
        String sessionCode = plannedSession();
        List<Map<String, Object>> quizzes = preparedQuizzes(sessionCode);
        answerAll(sessionCode, quizzes, 0);

        ResponseEntity<Map<String, Object>> response = generateSummary(sessionCode);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("hasWrongAnswers")).isEqualTo(false);
        assertThat(response.getBody().get("wrongAnswerCount")).isEqualTo(0);
        assertThat((List<?>) response.getBody().get("topics")).isEmpty();
        assertThat(fakeSummary().callCount()).isZero();
    }

    @Test
    @DisplayName("퀴즈를 다 풀지 않으면 요약을 만들 수 없다")
    void rejectsWhenQuizNotCompleted() {
        String sessionCode = plannedSession();
        List<Map<String, Object>> quizzes = preparedQuizzes(sessionCode);
        // 5문제 중 3문제만 답한다
        answer(sessionCode, quizzes.get(0).get("id"), 0);
        answer(sessionCode, quizzes.get(1).get("id"), 1);
        answer(sessionCode, quizzes.get(2).get("id"), 0);

        ResponseEntity<Map<String, Object>> rejected = generateSummary(sessionCode);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejected.getBody().get("code")).isEqualTo("QUIZ_NOT_COMPLETED");
        assertThat(fakeSummary().callCount()).isZero();
    }

    @Test
    @DisplayName("아직 요약을 만들지 않은 세션의 조회는 404다")
    void summaryNotFoundBeforeGeneration() {
        String sessionCode = plannedSession();

        ResponseEntity<Map<String, Object>> response = findSummary(sessionCode);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("WRONG_ANSWER_SUMMARY_NOT_FOUND");
    }
}
