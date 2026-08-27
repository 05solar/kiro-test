package com.naeil.study.curriculum;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.analysis.client.AiAnalysisClient;
import com.naeil.study.analysis.client.FakeAiAnalysisClient;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
 * 9단계 동적 재조정을 시간을 제어하며 끝까지 검증하는 통합 테스트.
 *
 * <p>실제 시각으로는 시작과 완료 사이가 0분에 가까워 시간 부족 상황을 만들 수 없다. 그래서
 * {@link MutableClock} 으로 시각을 앞으로 돌려, 예상보다 오래 걸린 학습이 남은 계획을 어떻게
 * 줄이고 제외하는지, 그리고 그 결과가 재접속 후에도 복구되는지 확인한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@DisplayName("동적 재조정 API 통합 - 시간 부족 시 축소와 제외")
class CurriculumReallocationApiIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 8, 27, 10, 0, 0);

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.local.root-path", () -> storageRoot.toString());
    }

    /** 시각을 앞으로 돌릴 수 있는 시계. 통합 테스트에서 시간 경과를 재현한다. */
    static final class MutableClock extends Clock {

        private volatile Instant instant = T0.atZone(ZONE).toInstant();

        void moveTo(LocalDateTime at) {
            this.instant = at.atZone(ZONE).toInstant();
        }

        void reset() {
            this.instant = T0.atZone(ZONE).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        AiAnalysisClient aiAnalysisClient() {
            return new FakeAiAnalysisClient();
        }

        @Bean
        Clock clock() {
            return new MutableClock();
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
    private Clock clock;

    private FakeAiAnalysisClient fakeAi() {
        return (FakeAiAnalysisClient) aiAnalysisClient;
    }

    private MutableClock clock() {
        return (MutableClock) clock;
    }

    @BeforeEach
    void reset() {
        fakeAi().reset();
        clock().reset();
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
        return new AiTopicResult(title, "요약", List.of("개념"), importance, minutes,
                false, false, false, false, List.of("DOC_1"));
    }

    /** 시험이 T0+3시간이고 남은 시간 150분인 계획을 만든다. */
    private String plannedSession(int availableStudyMinutes, AiTopicResult... topics) {
        fakeAi().respondMergeWith(() -> new AiTopicAnalysisResult(List.of(topics)));

        String sessionCode = (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");

        restTemplate.exchange("/api/sessions/" + sessionCode + "/exam", HttpMethod.PUT,
                jsonRequest("""
                        {"subject":"운영체제","examAt":"2026-08-27T13:00:00","availableStudyMinutes":%d}
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

    private ResponseEntity<Map<String, Object>> getCurriculum(String sessionCode) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/curriculum",
                HttpMethod.GET, null, JSON_MAP);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> steps(String sessionCode) {
        return (List<Map<String, Object>>) getCurriculum(sessionCode).getBody().get("steps");
    }

    private Object stepId(String sessionCode, int index) {
        return steps(sessionCode).get(index).get("id");
    }

    private ResponseEntity<Map<String, Object>> start(String sessionCode, Object stepId) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/steps/" + stepId + "/start",
                HttpMethod.POST, null, JSON_MAP);
    }

    private ResponseEntity<Map<String, Object>> complete(String sessionCode, Object stepId) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/steps/" + stepId + "/complete",
                HttpMethod.POST, null, JSON_MAP);
    }

    @Test
    @DisplayName("예상보다 오래 학습하면 남은 단계를 줄이고 최소 시간도 못 맞추는 단계는 제외한다")
    void shrinksAndSkipsWhenStudyRunsLong() {
        // 계획: VH60 / HIGH60 / MEDIUM30 (합 150 = 남은 시간)
        String sessionCode = plannedSession(150,
                aiTopic("프로세스와 스레드", "VERY_HIGH", 60),
                aiTopic("CPU 스케줄링", "HIGH", 60),
                aiTopic("교착상태", "MEDIUM", 60));

        List<Map<String, Object>> planned = steps(sessionCode);
        assertThat(planned).hasSize(3);
        Object firstId = planned.get(0).get("id");
        Object secondId = planned.get(1).get("id");
        Object thirdId = planned.get(2).get("id");

        // 첫 단계를 시작하고 120분이 지난 뒤 완료한다. 예산 남은 시간 = 150 - 120 = 30
        start(sessionCode, firstId);
        clock().moveTo(T0.plusMinutes(120));
        ResponseEntity<Map<String, Object>> completed = complete(sessionCode, firstId);

        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> time = (Map<String, Object>) completed.getBody().get("time");
        assertThat(time.get("remainingStudyMinutes")).isEqualTo(30);

        @SuppressWarnings("unchecked")
        Map<String, Object> reallocation = (Map<String, Object>) completed.getBody().get("reallocation");
        assertThat(reallocation.get("changed")).isEqualTo(true);

        // 다음 단계는 HIGH (order 2). MEDIUM 은 시간이 없어 제외된다.
        @SuppressWarnings("unchecked")
        Map<String, Object> nextStep = (Map<String, Object>) completed.getBody().get("nextStep");
        assertThat(nextStep.get("stepId")).isEqualTo(secondId);
        assertThat(nextStep.get("status")).isEqualTo("PENDING");
        assertThat(completed.getBody().get("curriculumCompleted")).isEqualTo(false);

        // 재접속: 다른 기기에서 세션 코드만으로 상태가 복구된다
        List<Map<String, Object>> restored = steps(sessionCode);
        assertThat(restored.get(0).get("status")).isEqualTo("COMPLETED");
        assertThat(restored.get(0).get("actualStudyMinutes")).isEqualTo(120);

        Map<String, Object> high = restored.get(1);
        assertThat(high.get("id")).isEqualTo(secondId);
        assertThat(high.get("status")).isEqualTo("PENDING");
        assertThat((Integer) high.get("allocatedMinutes")).isLessThanOrEqualTo(30);

        Map<String, Object> medium = restored.get(2);
        assertThat(medium.get("id")).isEqualTo(thirdId);
        assertThat(medium.get("status")).isEqualTo("SKIPPED");
        assertThat(medium.get("allocatedMinutes")).isEqualTo(0);
        assertThat(medium.get("skipReason")).isEqualTo("TIME_CONSTRAINT");

        // 진행률: 완료 1 + 제외 1 = 처리 2 / 전체 3 → 67%
        @SuppressWarnings("unchecked")
        Map<String, Object> progress = (Map<String, Object>) getCurriculum(sessionCode).getBody().get("progress");
        assertThat(progress.get("completedSteps")).isEqualTo(1);
        assertThat(progress.get("skippedSteps")).isEqualTo(1);
        assertThat(progress.get("totalSteps")).isEqualTo(3);
        assertThat(progress.get("percentage")).isEqualTo(67);
    }

    @Test
    @DisplayName("남은 PENDING 단계 배정 시간의 합은 재계산된 남은 시간을 넘지 않는다")
    void pendingAllocationsNeverExceedRemaining() {
        String sessionCode = plannedSession(150,
                aiTopic("프로세스와 스레드", "VERY_HIGH", 60),
                aiTopic("CPU 스케줄링", "HIGH", 60),
                aiTopic("교착상태", "MEDIUM", 60));

        Object firstId = stepId(sessionCode, 0);
        start(sessionCode, firstId);
        clock().moveTo(T0.plusMinutes(120));
        complete(sessionCode, firstId);

        int pendingSum = steps(sessionCode).stream()
                .filter(step -> "PENDING".equals(step.get("status")))
                .mapToInt(step -> (Integer) step.get("allocatedMinutes"))
                .sum();
        assertThat(pendingSum).isLessThanOrEqualTo(30);
    }
}
