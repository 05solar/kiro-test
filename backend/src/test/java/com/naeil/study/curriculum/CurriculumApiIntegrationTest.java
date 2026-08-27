package com.naeil.study.curriculum;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.analysis.client.AiAnalysisClient;
import com.naeil.study.analysis.client.FakeAiAnalysisClient;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicResult;
import com.naeil.study.curriculum.entity.Curriculum;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.repository.CurriculumRepository;
import com.naeil.study.curriculum.repository.StudyStepRepository;
import com.naeil.study.session.entity.SessionStatus;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.repository.StudySessionRepository;
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
 * 7단계 완료 시나리오를 검증하는 통합 테스트.
 *
 * <pre>
 * 세션 → 시험 정보 → 자료 업로드/파싱 → 분석(Topic) → 학습 계획 생성 → 조회
 * </pre>
 *
 * <p>AI는 {@link FakeAiAnalysisClient} 로 대체한다. 계획 생성 자체는 AI를 쓰지 않는다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@DisplayName("학습 계획 API 통합 - 남은 시간 기반 계획")
class CurriculumApiIntegrationTest {

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
    private CurriculumRepository curriculumRepository;

    @Autowired
    private StudyStepRepository studyStepRepository;

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

    /** 분석까지 끝나 계획을 만들 수 있는 세션을 만든다. */
    private String analyzedSession(int availableStudyMinutes) {
        String sessionCode = (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");

        restTemplate.exchange("/api/sessions/" + sessionCode + "/exam", HttpMethod.PUT,
                jsonRequest("""
                        {"subject":"운영체제","examAt":"2030-01-01T10:00:00","availableStudyMinutes":%d}
                        """.formatted(availableStudyMinutes)), JSON_MAP);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", part("운영체제.txt",
                "운영체제에서 프로세스는 실행 중인 프로그램을 의미한다. CPU 스케줄링은 준비 큐를 다룬다."));
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

    private void givenTopics(AiTopicResult... topics) {
        fakeAi().respondMergeWith(() -> new AiTopicAnalysisResult(List.of(topics)));
    }

    private AiTopicResult aiTopic(String title, String importance, int minutes, boolean mustStudy) {
        return new AiTopicResult(title, "요약", List.of("개념"), importance, minutes,
                false, false, false, mustStudy, List.of("DOC_1"));
    }

    private ResponseEntity<Map<String, Object>> createCurriculum(String sessionCode) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/curriculum",
                HttpMethod.POST, null, JSON_MAP);
    }

    private ResponseEntity<Map<String, Object>> getCurriculum(String sessionCode) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/curriculum",
                HttpMethod.GET, null, JSON_MAP);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> stepsOf(ResponseEntity<Map<String, Object>> response) {
        return (List<Map<String, Object>>) response.getBody().get("steps");
    }

    private int totalAllocated(List<Map<String, Object>> steps) {
        return steps.stream().mapToInt(step -> (Integer) step.get("allocatedMinutes")).sum();
    }

    @Test
    @DisplayName("남은 시간 안에 들어가는 학습 계획을 만들고 조회할 수 있다")
    void createsAndReadsCurriculum() {
        givenTopics(
                aiTopic("프로세스와 스레드", "VERY_HIGH", 50, false),
                aiTopic("CPU 스케줄링", "VERY_HIGH", 45, false),
                aiTopic("가상 메모리", "HIGH", 55, false),
                aiTopic("파일 시스템", "MEDIUM", 40, false),
                aiTopic("입출력", "LOW", 30, false));
        String sessionCode = analyzedSession(180);

        ResponseEntity<Map<String, Object>> created = createCurriculum(sessionCode);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("initialRemainingMinutes")).isEqualTo(180);
        assertThat(created.getBody().get("status")).isEqualTo("CREATED");

        List<Map<String, Object>> steps = stepsOf(created);
        assertThat(steps).isNotEmpty();
        assertThat(totalAllocated(steps)).isLessThanOrEqualTo(180);
        assertThat(created.getBody().get("totalAllocatedMinutes")).isEqualTo(totalAllocated(steps));
        assertThat(steps).allSatisfy(step -> {
            assertThat(step.get("status")).isEqualTo("PENDING");
            assertThat((Integer) step.get("allocatedMinutes")).isGreaterThanOrEqualTo(5);
        });

        ResponseEntity<Map<String, Object>> found = getCurriculum(sessionCode);
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().get("curriculumId")).isEqualTo(created.getBody().get("curriculumId"));
        assertThat(stepsOf(found)).hasSameSizeAs(steps);
    }

    @Test
    @DisplayName("배정 시간의 합이 남은 학습 시간을 넘지 않는다")
    void neverExceedsRemainingMinutes() {
        givenTopics(
                aiTopic("A", "VERY_HIGH", 120, false),
                aiTopic("B", "VERY_HIGH", 120, false),
                aiTopic("C", "HIGH", 120, false),
                aiTopic("D", "MEDIUM", 120, false));
        String sessionCode = analyzedSession(120);

        ResponseEntity<Map<String, Object>> created = createCurriculum(sessionCode);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(totalAllocated(stepsOf(created))).isLessThanOrEqualTo(120);

        StudySession session = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        Curriculum curriculum = curriculumRepository.findByStudySessionId(session.getId()).orElseThrow();
        assertThat(curriculum.getTotalAllocatedMinutes())
                .isLessThanOrEqualTo(curriculum.getInitialRemainingMinutes());
    }

    @Test
    @DisplayName("반드시 학습할 주제는 중요도가 낮아도 계획에 남는다")
    void keepsMustStudyTopic() {
        givenTopics(
                aiTopic("CPU 스케줄링", "VERY_HIGH", 60, false),
                aiTopic("교착상태", "LOW", 30, true));
        String sessionCode = analyzedSession(60);

        ResponseEntity<Map<String, Object>> created = createCurriculum(sessionCode);

        List<Map<String, Object>> steps = stepsOf(created);
        assertThat(steps).anySatisfy(step -> {
            assertThat(step.get("title")).isEqualTo("교착상태");
            assertThat(step.get("mandatory")).isEqualTo(true);
            assertThat(String.valueOf(step.get("priorityReasons"))).contains("MUST_STUDY");
        });
    }

    @Test
    @DisplayName("계획 단계는 원래 학습 순서를 따른다")
    void keepsTopicOrder() {
        givenTopics(
                aiTopic("프로세스", "MEDIUM", 30, false),
                aiTopic("CPU 스케줄링", "VERY_HIGH", 30, false),
                aiTopic("교착상태", "HIGH", 30, false));
        String sessionCode = analyzedSession(200);

        List<Map<String, Object>> steps = stepsOf(createCurriculum(sessionCode));

        assertThat(steps.stream().filter(step -> "STUDY".equals(step.get("type"))))
                .extracting(step -> step.get("title"))
                .containsExactly("프로세스", "CPU 스케줄링", "교착상태");
    }

    @Test
    @DisplayName("시간이 남으면 마지막에 복습 단계를 만든다")
    void addsReviewStep() {
        givenTopics(aiTopic("프로세스", "VERY_HIGH", 30, false));
        String sessionCode = analyzedSession(120);

        List<Map<String, Object>> steps = stepsOf(createCurriculum(sessionCode));

        Map<String, Object> last = steps.get(steps.size() - 1);
        assertThat(last.get("type")).isEqualTo("REVIEW");
        assertThat(last.get("topicId")).isNull();
        assertThat(last.get("importance")).isNull();
    }

    @Test
    @DisplayName("계획을 만들어도 세션 상태는 READY를 유지한다")
    void keepsSessionReady() {
        givenTopics(aiTopic("프로세스", "VERY_HIGH", 30, false));
        String sessionCode = analyzedSession(120);

        createCurriculum(sessionCode);

        StudySession session = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.READY);
        assertThat(session.getCurrentStepOrder()).isNull();
    }

    @Test
    @DisplayName("다시 요청하면 기존 계획을 그대로 돌려준다")
    void isIdempotent() {
        givenTopics(aiTopic("프로세스", "VERY_HIGH", 30, false));
        String sessionCode = analyzedSession(120);
        ResponseEntity<Map<String, Object>> first = createCurriculum(sessionCode);

        ResponseEntity<Map<String, Object>> second = createCurriculum(sessionCode);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().get("curriculumId")).isEqualTo(first.getBody().get("curriculumId"));

        StudySession session = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        Curriculum curriculum = curriculumRepository.findByStudySessionId(session.getId()).orElseThrow();
        assertThat(studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(curriculum.getId()))
                .hasSameSizeAs(stepsOf(first));
    }

    @Test
    @DisplayName("분석하지 않은 세션은 계획을 만들 수 없다")
    void failsWithoutAnalysis() {
        String sessionCode = (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");
        restTemplate.exchange("/api/sessions/" + sessionCode + "/exam", HttpMethod.PUT,
                jsonRequest("""
                        {"subject":"운영체제","examAt":"2030-01-01T10:00:00","availableStudyMinutes":180}
                        """), JSON_MAP);

        ResponseEntity<Map<String, Object>> response = createCurriculum(sessionCode);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("SESSION_NOT_READY");
    }

    @Test
    @DisplayName("아직 계획을 만들지 않았으면 404")
    void returns404WhenCurriculumIsAbsent() {
        givenTopics(aiTopic("프로세스", "VERY_HIGH", 30, false));
        String sessionCode = analyzedSession(120);

        ResponseEntity<Map<String, Object>> response = getCurriculum(sessionCode);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("CURRICULUM_NOT_FOUND");
    }

    @Test
    @DisplayName("존재하지 않는 세션이면 404")
    void returns404ForUnknownSession() {
        assertThat(createCurriculum("ZZZZZZZZ").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getCurriculum("ZZZZZZZZ").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("다른 세션의 계획은 보이지 않는다")
    void doesNotLeakCurriculumOfOtherSession() {
        givenTopics(aiTopic("프로세스", "VERY_HIGH", 30, false));
        String ownerCode = analyzedSession(120);
        createCurriculum(ownerCode);
        String otherCode = analyzedSession(120);

        assertThat(getCurriculum(otherCode).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("배정 시간은 계획 시점 권장 시간을 넘지 않는다")
    void neverExceedsEstimatedMinutes() {
        givenTopics(
                aiTopic("A", "VERY_HIGH", 20, false),
                aiTopic("B", "HIGH", 30, false));
        String sessionCode = analyzedSession(300);

        List<Map<String, Object>> steps = stepsOf(createCurriculum(sessionCode));

        assertThat(steps.stream().filter(step -> "STUDY".equals(step.get("type"))))
                .allSatisfy(step -> assertThat((Integer) step.get("allocatedMinutes"))
                        .isLessThanOrEqualTo((Integer) step.get("originalEstimatedMinutes")));
    }
}
