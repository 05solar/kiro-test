package com.naeil.study.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.analysis.client.AiAnalysisClient;
import com.naeil.study.analysis.client.FakeAiAnalysisClient;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicResult;
import com.naeil.study.quiz.client.AiQuizClient;
import com.naeil.study.quiz.client.FakeAiQuizClient;
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
 * 10단계 시나리오를 검증하는 통합 테스트.
 *
 * <pre>
 * 학습 완료 → 퀴즈 생성(정답 미노출) → 답안 제출 → 채점 → 점수 집계
 * </pre>
 *
 * <p>AI는 가짜 구현을 쓴다. 실제 API를 부르지 않는다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@DisplayName("퀴즈 API 통합 - 생성부터 채점까지")
class QuizApiIntegrationTest {

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

    private FakeAiAnalysisClient fakeAnalysis() {
        return (FakeAiAnalysisClient) aiAnalysisClient;
    }

    private FakeAiQuizClient fakeQuiz() {
        return (FakeAiQuizClient) aiQuizClient;
    }

    @BeforeEach
    void resetAi() {
        fakeAnalysis().reset();
        fakeQuiz().reset();
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

    private AiTopicResult aiTopic(String title, String importance, int minutes) {
        return new AiTopicResult(title, "요약", List.of("Round Robin"), importance, minutes,
                false, false, false, false, List.of("DOC_1"));
    }

    /** 계획까지 만들어 둔 세션을 준비한다. */
    private String plannedSession(AiTopicResult... topics) {
        fakeAnalysis().respondMergeWith(() -> new AiTopicAnalysisResult(List.of(topics)));

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

    /** 첫 STUDY 단계를 완료하고 그 Topic id 를 돌려준다. */
    private String completeFirstStep(String sessionCode) {
        Map<String, Object> first = steps(sessionCode).get(0);
        Object stepId = first.get("id");
        restTemplate.exchange("/api/sessions/" + sessionCode + "/steps/" + stepId + "/start",
                HttpMethod.POST, null, JSON_MAP);
        restTemplate.exchange("/api/sessions/" + sessionCode + "/steps/" + stepId + "/complete",
                HttpMethod.POST, null, JSON_MAP);
        return String.valueOf(first.get("topicId"));
    }

    private ResponseEntity<Map<String, Object>> generateQuizzes(String sessionCode, String topicId) {
        return restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/topics/" + topicId + "/quizzes",
                HttpMethod.POST, null, JSON_MAP);
    }

    private ResponseEntity<Map<String, Object>> answer(String sessionCode, Object quizId, int selectedIndex) {
        return restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/quizzes/" + quizId + "/answer",
                HttpMethod.POST, jsonRequest("{\"selectedIndex\":" + selectedIndex + "}"), JSON_MAP);
    }

    private ResponseEntity<Map<String, Object>> results(String sessionCode, String topicId) {
        return restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/topics/" + topicId + "/quiz-results",
                HttpMethod.GET, null, JSON_MAP);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> quizzesOf(ResponseEntity<Map<String, Object>> response) {
        return (List<Map<String, Object>>) response.getBody().get("quizzes");
    }

    @Test
    @DisplayName("학습 완료 후 퀴즈를 생성하면 정답 정보 없이 문제가 내려온다")
    void generatesQuizzesWithoutRevealingAnswers() {
        String sessionCode = plannedSession(aiTopic("CPU 스케줄링", "VERY_HIGH", 50));
        String topicId = completeFirstStep(sessionCode);

        ResponseEntity<Map<String, Object>> created = generateQuizzes(sessionCode, topicId);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        List<Map<String, Object>> quizzes = quizzesOf(created);
        assertThat(quizzes).hasSize(5);
        for (Map<String, Object> quiz : quizzes) {
            assertThat(quiz).doesNotContainKeys("correctIndex", "explanation");
            assertThat((List<?>) quiz.get("options")).hasSize(4);
        }
        // AI 요청에는 강의자료 추출 구간이 들어간다
        assertThat(fakeQuiz().requests().get(0).sourceContext()).contains("Round Robin");
    }

    @Test
    @DisplayName("다시 생성을 요청해도 AI를 부르지 않고 기존 퀴즈를 돌려준다")
    void generationIsIdempotent() {
        String sessionCode = plannedSession(aiTopic("CPU 스케줄링", "VERY_HIGH", 50));
        String topicId = completeFirstStep(sessionCode);
        generateQuizzes(sessionCode, topicId);

        ResponseEntity<Map<String, Object>> again = generateQuizzes(sessionCode, topicId);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(quizzesOf(again)).hasSize(5);
        assertThat(fakeQuiz().callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("학습을 완료하지 않은 Topic 은 퀴즈를 만들 수 없다")
    void rejectsWhenStudyNotCompleted() {
        String sessionCode = plannedSession(
                aiTopic("CPU 스케줄링", "VERY_HIGH", 50),
                aiTopic("교착상태", "HIGH", 40));
        // 첫 단계만 완료한다. 둘째 Topic 은 PENDING 이다
        completeFirstStep(sessionCode);
        String secondTopicId = String.valueOf(steps(sessionCode).get(1).get("topicId"));

        ResponseEntity<Map<String, Object>> rejected = generateQuizzes(sessionCode, secondTopicId);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejected.getBody().get("code")).isEqualTo("TOPIC_STUDY_NOT_COMPLETED");
        assertThat(fakeQuiz().callCount()).isZero();
    }

    @Test
    @DisplayName("답안을 제출하면 채점 결과와 정답·해설이 반환된다")
    void answersAndGrades() {
        String sessionCode = plannedSession(aiTopic("CPU 스케줄링", "VERY_HIGH", 50));
        String topicId = completeFirstStep(sessionCode);
        List<Map<String, Object>> quizzes = quizzesOf(generateQuizzes(sessionCode, topicId));

        // Fake 응답의 첫 문제 정답은 0이다
        ResponseEntity<Map<String, Object>> correct = answer(sessionCode, quizzes.get(0).get("id"), 0);
        assertThat(correct.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(correct.getBody().get("correct")).isEqualTo(true);
        assertThat(correct.getBody().get("correctIndex")).isEqualTo(0);
        assertThat(correct.getBody().get("explanation")).isNotNull();

        ResponseEntity<Map<String, Object>> wrong = answer(sessionCode, quizzes.get(1).get("id"), 0);
        assertThat(wrong.getBody().get("correct")).isEqualTo(false);
        assertThat(wrong.getBody().get("correctIndex")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 문제에 다시 답해도 첫 답안이 유지된다")
    void answerIsImmutable() {
        String sessionCode = plannedSession(aiTopic("CPU 스케줄링", "VERY_HIGH", 50));
        String topicId = completeFirstStep(sessionCode);
        Object quizId = quizzesOf(generateQuizzes(sessionCode, topicId)).get(0).get("id");

        answer(sessionCode, quizId, 1);   // 오답
        ResponseEntity<Map<String, Object>> again = answer(sessionCode, quizId, 0);   // 정답 시도

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getBody().get("selectedIndex")).isEqualTo(1);
        assertThat(again.getBody().get("correct")).isEqualTo(false);
    }

    @Test
    @DisplayName("5문제 중 4문제를 맞히면 점수가 80% 로 집계된다")
    void aggregatesScore() {
        String sessionCode = plannedSession(aiTopic("CPU 스케줄링", "VERY_HIGH", 50));
        String topicId = completeFirstStep(sessionCode);
        List<Map<String, Object>> quizzes = quizzesOf(generateQuizzes(sessionCode, topicId));

        // Fake 정답: [0, 1, 2, 3, 0]. 마지막 문제만 틀린다
        int[] answers = {0, 1, 2, 3, 1};
        for (int i = 0; i < quizzes.size(); i++) {
            answer(sessionCode, quizzes.get(i).get("id"), answers[i]);
        }

        ResponseEntity<Map<String, Object>> aggregated = results(sessionCode, topicId);
        assertThat(aggregated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aggregated.getBody().get("totalQuestions")).isEqualTo(5);
        assertThat(aggregated.getBody().get("answeredQuestions")).isEqualTo(5);
        assertThat(aggregated.getBody().get("correctAnswers")).isEqualTo(4);
        assertThat(aggregated.getBody().get("scorePercentage")).isEqualTo(80);
        assertThat(aggregated.getBody().get("completed")).isEqualTo(true);
    }

    @Test
    @DisplayName("아직 다 풀지 않으면 completed 가 아니다")
    void reportsIncompleteResults() {
        String sessionCode = plannedSession(aiTopic("CPU 스케줄링", "VERY_HIGH", 50));
        String topicId = completeFirstStep(sessionCode);
        List<Map<String, Object>> quizzes = quizzesOf(generateQuizzes(sessionCode, topicId));

        answer(sessionCode, quizzes.get(0).get("id"), 0);
        answer(sessionCode, quizzes.get(1).get("id"), 0);
        answer(sessionCode, quizzes.get(2).get("id"), 2);

        ResponseEntity<Map<String, Object>> aggregated = results(sessionCode, topicId);
        assertThat(aggregated.getBody().get("answeredQuestions")).isEqualTo(3);
        assertThat(aggregated.getBody().get("correctAnswers")).isEqualTo(2);
        assertThat(aggregated.getBody().get("completed")).isEqualTo(false);
    }

    @Test
    @DisplayName("다른 세션의 Topic 과 퀴즈에는 접근할 수 없다")
    void guardsOwnership() {
        String owner = plannedSession(aiTopic("CPU 스케줄링", "VERY_HIGH", 50));
        String ownerTopicId = completeFirstStep(owner);
        Object ownerQuizId = quizzesOf(generateQuizzes(owner, ownerTopicId)).get(0).get("id");

        String other = plannedSession(aiTopic("네트워크 계층", "VERY_HIGH", 50));

        ResponseEntity<Map<String, Object>> topicAccess = generateQuizzes(other, ownerTopicId);
        assertThat(topicAccess.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(topicAccess.getBody().get("code")).isEqualTo("TOPIC_NOT_FOUND");

        ResponseEntity<Map<String, Object>> quizAccess = answer(other, ownerQuizId, 0);
        assertThat(quizAccess.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(quizAccess.getBody().get("code")).isEqualTo("QUIZ_NOT_FOUND");
    }

    @Test
    @DisplayName("아직 퀴즈를 생성하지 않은 Topic 의 조회와 집계는 404다")
    void quizNotFoundBeforeGeneration() {
        String sessionCode = plannedSession(aiTopic("CPU 스케줄링", "VERY_HIGH", 50));
        String topicId = completeFirstStep(sessionCode);

        ResponseEntity<Map<String, Object>> list = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/topics/" + topicId + "/quizzes",
                HttpMethod.GET, null, JSON_MAP);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(list.getBody().get("code")).isEqualTo("QUIZ_NOT_FOUND");

        ResponseEntity<Map<String, Object>> aggregated = results(sessionCode, topicId);
        assertThat(aggregated.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("재분석하면 옛 계획·퀴즈·답안이 함께 사라진다")
    void reanalysisClearsDerivedData() {
        String sessionCode = plannedSession(aiTopic("CPU 스케줄링", "VERY_HIGH", 50));
        String topicId = completeFirstStep(sessionCode);
        List<Map<String, Object>> quizzes = quizzesOf(generateQuizzes(sessionCode, topicId));
        answer(sessionCode, quizzes.get(0).get("id"), 0);

        // 자료·맥락이 바뀌었다고 가정하고 다시 분석한다. Topic 이 통째로 교체된다.
        ResponseEntity<Map<String, Object>> reanalyzed = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/analysis", HttpMethod.POST, null, JSON_MAP);
        assertThat(reanalyzed.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 옛 Topic 에서 파생된 것들은 모두 무효가 되어 사라져야 한다 (FK 500 회귀 방지)
        ResponseEntity<Map<String, Object>> curriculum = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/curriculum", HttpMethod.GET, null, JSON_MAP);
        assertThat(curriculum.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(curriculum.getBody().get("code")).isEqualTo("CURRICULUM_NOT_FOUND");

        ResponseEntity<Map<String, Object>> oldTopicQuizzes = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/topics/" + topicId + "/quizzes",
                HttpMethod.GET, null, JSON_MAP);
        assertThat(oldTopicQuizzes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
