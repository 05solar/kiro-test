package com.naeil.study.curriculum;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.analysis.client.AiAnalysisClient;
import com.naeil.study.analysis.client.FakeAiAnalysisClient;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicResult;
import com.naeil.study.curriculum.entity.Curriculum;
import com.naeil.study.curriculum.entity.CurriculumStatus;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.repository.CurriculumRepository;
import com.naeil.study.curriculum.repository.StudyStepRepository;
import com.naeil.study.session.entity.SessionStatus;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.repository.StudySessionRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * 8단계 완료 시나리오를 검증하는 통합 테스트.
 *
 * <pre>
 * 계획 생성 → STEP 시작 → 재접속 복구 → STEP 완료 → 다음 STEP → 계획 완료
 * </pre>
 *
 * <p>다른 기기에서 세션 코드만으로 접속했을 때 학습 상태가 서버 데이터만으로 복구되는지를
 * 함께 확인한다. 클라이언트가 진행 상태를 들고 있지 않아도 이어서 학습할 수 있어야 한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@DisplayName("학습 진행 API 통합 - StudyStep 시작과 완료")
class StudyStepApiIntegrationTest {

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

    /** 학습 계획까지 만들어 둔 세션을 준비한다. */
    private String plannedSession(int availableStudyMinutes, AiTopicResult... topics) {
        fakeAi().respondMergeWith(() -> new AiTopicAnalysisResult(List.of(topics)));

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
        restTemplate.exchange("/api/sessions/" + sessionCode + "/curriculum",
                HttpMethod.POST, null, JSON_MAP);
        return sessionCode;
    }

    private AiTopicResult aiTopic(String title, String importance, int minutes) {
        return new AiTopicResult(title, "요약", List.of("개념"), importance, minutes,
                false, false, false, false, List.of("DOC_1"));
    }

    private ResponseEntity<Map<String, Object>> getCurriculum(String sessionCode) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/curriculum",
                HttpMethod.GET, null, JSON_MAP);
    }

    private ResponseEntity<Map<String, Object>> start(String sessionCode, Object stepId) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/steps/" + stepId + "/start",
                HttpMethod.POST, null, JSON_MAP);
    }

    private ResponseEntity<Map<String, Object>> complete(String sessionCode, Object stepId) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/steps/" + stepId + "/complete",
                HttpMethod.POST, null, JSON_MAP);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> stepsOf(ResponseEntity<Map<String, Object>> response) {
        return (List<Map<String, Object>>) response.getBody().get("steps");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> progressOf(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody().get("progress");
    }

    private List<Map<String, Object>> steps(String sessionCode) {
        return stepsOf(getCurriculum(sessionCode));
    }

    private Object stepId(String sessionCode, int index) {
        return steps(sessionCode).get(index).get("id");
    }

    private StudyStep stepEntity(Object stepId) {
        return studyStepRepository.findById(UUID.fromString(String.valueOf(stepId))).orElseThrow();
    }

    private StudySession session(String sessionCode) {
        return studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
    }

    private Curriculum curriculum(String sessionCode) {
        return curriculumRepository.findByStudySessionId(session(sessionCode).getId()).orElseThrow();
    }

    @Test
    @DisplayName("첫 단계를 시작하면 세션과 계획이 함께 진행 중이 된다")
    void startsFirstStep() {
        String sessionCode = plannedSession(180,
                aiTopic("프로세스와 스레드", "VERY_HIGH", 50),
                aiTopic("CPU 스케줄링", "HIGH", 40));

        ResponseEntity<Map<String, Object>> started = start(sessionCode, stepId(sessionCode, 0));

        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(started.getBody().get("status")).isEqualTo("IN_PROGRESS");
        assertThat(started.getBody().get("stepOrder")).isEqualTo(1);
        assertThat(started.getBody().get("startedAt")).isNotNull();
        assertThat(started.getBody().get("completedAt")).isNull();
        assertThat(started.getBody().get("actualStudyMinutes")).isNull();

        assertThat(session(sessionCode).getCurrentStepOrder()).isEqualTo(1);
        assertThat(session(sessionCode).getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(curriculum(sessionCode).getStatus()).isEqualTo(CurriculumStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("앞선 단계를 건너뛰거나 두 단계를 동시에 시작할 수 없다")
    void guardsStepOrderAndConcurrency() {
        String sessionCode = plannedSession(180,
                aiTopic("프로세스와 스레드", "VERY_HIGH", 50),
                aiTopic("CPU 스케줄링", "HIGH", 40));
        Object first = stepId(sessionCode, 0);
        Object second = stepId(sessionCode, 1);

        ResponseEntity<Map<String, Object>> skipped = start(sessionCode, second);
        assertThat(skipped.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(skipped.getBody().get("code")).isEqualTo("INVALID_STUDY_STEP_ORDER");

        start(sessionCode, first);

        ResponseEntity<Map<String, Object>> concurrent = start(sessionCode, second);
        assertThat(concurrent.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(concurrent.getBody().get("code")).isEqualTo("ANOTHER_STEP_IN_PROGRESS");
    }

    @Test
    @DisplayName("같은 단계를 다시 시작해도 시작 시각이 바뀌지 않는다")
    void startIsIdempotent() {
        String sessionCode = plannedSession(180, aiTopic("프로세스와 스레드", "VERY_HIGH", 50));
        Object first = stepId(sessionCode, 0);

        start(sessionCode, first);
        // 응답 JSON이 아니라 DB 값을 비교한다. 응답의 나노초 정밀도와 DB 저장 정밀도가 달라
        // 문자열끼리 비교하면 값이 바뀌지 않았는데도 다르게 보인다.
        LocalDateTime startedAt = stepEntity(first).getStartedAt();

        ResponseEntity<Map<String, Object>> again = start(sessionCode, first);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stepEntity(first).getStartedAt()).isEqualTo(startedAt);
    }

    @Test
    @DisplayName("다른 기기에서 세션 코드만으로 진행 중인 단계를 복구할 수 있다")
    void restoresProgressFromServer() {
        String sessionCode = plannedSession(180,
                aiTopic("프로세스와 스레드", "VERY_HIGH", 50),
                aiTopic("CPU 스케줄링", "HIGH", 40),
                aiTopic("교착상태", "MEDIUM", 30));
        start(sessionCode, stepId(sessionCode, 0));
        complete(sessionCode, stepId(sessionCode, 0));
        start(sessionCode, stepId(sessionCode, 1));

        // 브라우저를 닫았다가 다른 기기에서 다시 접속한 상황
        ResponseEntity<Map<String, Object>> restored = getCurriculum(sessionCode);

        assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> steps = stepsOf(restored);
        assertThat(steps.get(0).get("status")).isEqualTo("COMPLETED");
        assertThat(steps.get(0).get("actualStudyMinutes")).isNotNull();
        assertThat(steps.get(0).get("completedAt")).isNotNull();
        assertThat(steps.get(1).get("status")).isEqualTo("IN_PROGRESS");
        assertThat(steps.get(1).get("startedAt")).isNotNull();
        assertThat(steps.get(1).get("actualStudyMinutes")).isNull();
        assertThat(steps.get(2).get("status")).isEqualTo("PENDING");
        assertThat(steps.get(2).get("startedAt")).isNull();

        assertThat(session(sessionCode).getCurrentStepOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("단계를 완료하면 실제 학습시간을 기록하고 다음 단계를 알려준다")
    void completesStepAndReturnsNext() {
        String sessionCode = plannedSession(180,
                aiTopic("프로세스와 스레드", "VERY_HIGH", 50),
                aiTopic("CPU 스케줄링", "HIGH", 40));
        Object first = stepId(sessionCode, 0);
        start(sessionCode, first);

        ResponseEntity<Map<String, Object>> completed = complete(sessionCode, first);

        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody().get("curriculumCompleted")).isEqualTo(false);

        @SuppressWarnings("unchecked")
        Map<String, Object> completedStep = (Map<String, Object>) completed.getBody().get("completedStep");
        assertThat(completedStep.get("status")).isEqualTo("COMPLETED");
        assertThat(completedStep.get("completedAt")).isNotNull();
        assertThat((Integer) completedStep.get("actualStudyMinutes")).isGreaterThanOrEqualTo(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> nextStep = (Map<String, Object>) completed.getBody().get("nextStep");
        assertThat(nextStep.get("stepOrder")).isEqualTo(2);
        assertThat(nextStep.get("status")).isEqualTo("PENDING");

        // 완료했을 뿐 다음 단계를 시작한 것은 아니다
        assertThat(session(sessionCode).getCurrentStepOrder()).isNull();
    }

    @Test
    @DisplayName("완료 요청을 다시 보내도 기록이 바뀌지 않는다")
    void completeIsIdempotent() {
        String sessionCode = plannedSession(180, aiTopic("프로세스와 스레드", "VERY_HIGH", 50));
        Object first = stepId(sessionCode, 0);
        start(sessionCode, first);

        complete(sessionCode, first);
        LocalDateTime completedAt = stepEntity(first).getCompletedAt();
        Integer actualStudyMinutes = stepEntity(first).getActualStudyMinutes();

        ResponseEntity<Map<String, Object>> again = complete(sessionCode, first);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stepEntity(first).getCompletedAt()).isEqualTo(completedAt);
        assertThat(stepEntity(first).getActualStudyMinutes()).isEqualTo(actualStudyMinutes);
    }

    @Test
    @DisplayName("시작하지 않은 단계는 완료할 수 없고 완료한 단계는 다시 시작할 수 없다")
    void guardsInvalidTransitions() {
        String sessionCode = plannedSession(180,
                aiTopic("프로세스와 스레드", "VERY_HIGH", 50),
                aiTopic("CPU 스케줄링", "HIGH", 40));
        Object first = stepId(sessionCode, 0);

        ResponseEntity<Map<String, Object>> notStarted = complete(sessionCode, first);
        assertThat(notStarted.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(notStarted.getBody().get("code")).isEqualTo("STUDY_STEP_NOT_STARTED");

        start(sessionCode, first);
        complete(sessionCode, first);

        ResponseEntity<Map<String, Object>> restart = start(sessionCode, first);
        assertThat(restart.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(restart.getBody().get("code")).isEqualTo("STUDY_STEP_ALREADY_COMPLETED");
    }

    @Test
    @DisplayName("모든 단계를 마치면 계획은 완료되고 세션은 진행 중으로 남는다")
    void completesCurriculum() {
        String sessionCode = plannedSession(120,
                aiTopic("프로세스와 스레드", "VERY_HIGH", 50),
                aiTopic("CPU 스케줄링", "HIGH", 40));
        List<Map<String, Object>> planned = steps(sessionCode);

        boolean lastCompleted = false;
        for (int i = 0; i < planned.size(); i++) {
            Object id = planned.get(i).get("id");
            start(sessionCode, id);
            lastCompleted = (Boolean) complete(sessionCode, id).getBody().get("curriculumCompleted");
        }

        assertThat(lastCompleted).isTrue();
        assertThat(curriculum(sessionCode).getStatus()).isEqualTo(CurriculumStatus.COMPLETED);
        assertThat(session(sessionCode).getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);

        Map<String, Object> progress = progressOf(getCurriculum(sessionCode));
        assertThat(progress.get("completedSteps")).isEqualTo(planned.size());
        assertThat(progress.get("totalSteps")).isEqualTo(planned.size());
        assertThat(progress.get("percentage")).isEqualTo(100);
    }

    @Test
    @DisplayName("진행률은 완료한 단계 수로 계산한다")
    void reportsProgress() {
        String sessionCode = plannedSession(180,
                aiTopic("프로세스와 스레드", "VERY_HIGH", 50),
                aiTopic("CPU 스케줄링", "HIGH", 40),
                aiTopic("교착상태", "MEDIUM", 30),
                aiTopic("파일 시스템", "LOW", 20));
        List<Map<String, Object>> planned = steps(sessionCode);

        Map<String, Object> before = progressOf(getCurriculum(sessionCode));
        assertThat(before.get("completedSteps")).isEqualTo(0);
        assertThat(before.get("percentage")).isEqualTo(0);

        start(sessionCode, planned.get(0).get("id"));
        complete(sessionCode, planned.get(0).get("id"));

        Map<String, Object> after = progressOf(getCurriculum(sessionCode));
        assertThat(after.get("completedSteps")).isEqualTo(1);
        assertThat(after.get("totalSteps")).isEqualTo(planned.size());
        assertThat(after.get("percentage"))
                .isEqualTo((int) Math.round(100.0 / planned.size()));
    }

    @Test
    @DisplayName("학습을 진행해도 남은 학습 시간을 차감하지 않는다")
    void doesNotDeductRemainingStudyMinutes() {
        String sessionCode = plannedSession(180,
                aiTopic("프로세스와 스레드", "VERY_HIGH", 50),
                aiTopic("CPU 스케줄링", "HIGH", 40));
        Integer beforeRemaining = session(sessionCode).getRemainingStudyMinutes();
        List<Map<String, Object>> beforeSteps = steps(sessionCode);
        Object first = beforeSteps.get(0).get("id");

        start(sessionCode, first);
        complete(sessionCode, first);

        assertThat(session(sessionCode).getRemainingStudyMinutes()).isEqualTo(beforeRemaining);
        // 남은 단계의 배정 시간도 그대로다. 재배분은 9단계의 일이다.
        List<Map<String, Object>> afterSteps = steps(sessionCode);
        for (int i = 0; i < beforeSteps.size(); i++) {
            assertThat(afterSteps.get(i).get("allocatedMinutes"))
                    .isEqualTo(beforeSteps.get(i).get("allocatedMinutes"));
        }
    }

    @Test
    @DisplayName("다른 세션의 단계는 진행할 수 없다")
    void rejectsStepOfAnotherSession() {
        String owner = plannedSession(180, aiTopic("프로세스와 스레드", "VERY_HIGH", 50));
        String other = plannedSession(180, aiTopic("네트워크 계층", "VERY_HIGH", 50));
        Object otherStepId = stepId(other, 0);

        ResponseEntity<Map<String, Object>> started = start(owner, otherStepId);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(started.getBody().get("code")).isEqualTo("STUDY_STEP_NOT_FOUND");

        ResponseEntity<Map<String, Object>> completed = complete(owner, otherStepId);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(completed.getBody().get("code")).isEqualTo("STUDY_STEP_NOT_FOUND");

        assertThat(steps(other).get(0).get("status")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("학습 계획이 없는 세션은 단계를 진행할 수 없다")
    void rejectsWithoutCurriculum() {
        String sessionCode = (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");

        ResponseEntity<Map<String, Object>> started = start(sessionCode, UUID.randomUUID());

        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(started.getBody().get("code")).isEqualTo("CURRICULUM_NOT_FOUND");
    }
}
