package com.naeil.study.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.session.entity.SessionCodePolicy;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.repository.StudySessionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 1단계 완료 조건을 그대로 검증하는 통합 테스트.
 *
 * <pre>
 * POST /api/sessions → 8자리 코드 발급 → DB 저장
 *                    → GET /api/sessions/{code} → lastAccessedAt 갱신 → expiresAt 30일 연장
 * </pre>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@DisplayName("세션 API 통합 - 생성과 복구")
class SessionApiIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 27, 15, 30, 0);

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {
            };

    /** 시간 흐름을 직접 제어하기 위한 테스트용 Clock. */
    static class MutableClock extends Clock {

        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setNow(LocalDateTime now) {
            this.instant = now.atZone(ZONE).toInstant();
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
    static class TestClockConfig {

        @Bean
        Clock clock() {
            return new MutableClock(CREATED_AT.atZone(ZONE).toInstant());
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Autowired
    private Clock clock;

    private MutableClock testClock() {
        return (MutableClock) clock;
    }

    /**
     * 시험 정보 등록 요청 본문을 만든다.
     *
     * <p>과목명이 한글이므로 Content-Type에 UTF-8을 명시한다.
     * 명시하지 않으면 String 본문이 ISO-8859-1로 인코딩되어 과목명이 깨진다.
     */
    private HttpEntity<String> jsonRequest(String subject, String examAt, int availableStudyMinutes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        String body = """
                {"subject":"%s","examAt":"%s","availableStudyMinutes":%d}
                """.formatted(subject, examAt, availableStudyMinutes);
        return new HttpEntity<>(body, headers);
    }

    @Test
    @DisplayName("세션을 생성하고 다른 기기처럼 코드만으로 다시 불러올 수 있다")
    void createAndRestoreSession() {
        testClock().setNow(CREATED_AT);

        // 1. 세션 생성 → 201 + 8자리 코드
        ResponseEntity<Map<String, Object>> created =
                restTemplate.exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String sessionCode = (String) created.getBody().get("sessionCode");
        assertThat(sessionCode).matches(SessionCodePolicy.PATTERN);
        assertThat(created.getBody().get("status")).isEqualTo("CREATED");

        // 2. DB에 저장되었는지 확인
        StudySession stored = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        assertThat(stored.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(stored.getLastAccessedAt()).isEqualTo(CREATED_AT);
        assertThat(stored.getExpiresAt()).isEqualTo(CREATED_AT.plusDays(30));

        // 3. 5일 뒤 다른 기기에서 코드만으로 조회 → 200
        LocalDateTime accessedAt = CREATED_AT.plusDays(5);
        testClock().setNow(accessedAt);

        ResponseEntity<Map<String, Object>> found =
                restTemplate.exchange("/api/sessions/" + sessionCode, HttpMethod.GET, null, JSON_MAP);

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().get("sessionCode")).isEqualTo(sessionCode);
        assertThat(found.getBody().get("status")).isEqualTo("CREATED");
        assertThat(found.getBody()).containsKey("availableStudyMinutes");
        assertThat(found.getBody()).containsKey("remainingStudyMinutes");
        assertThat(found.getBody()).containsKey("currentStepOrder");
        assertThat(found.getBody().get("availableStudyMinutes")).isNull();
        assertThat(found.getBody().get("remainingStudyMinutes")).isNull();
        assertThat(found.getBody().get("createdAt")).isEqualTo("2026-08-27T15:30:00");
        assertThat(found.getBody().get("lastAccessedAt")).isEqualTo("2026-09-01T15:30:00");
        assertThat(found.getBody().get("expiresAt")).isEqualTo("2026-10-01T15:30:00");
        assertThat(found.getBody()).doesNotContainKey("id");

        // 4. 갱신 결과가 DB에도 반영되었는지 확인
        StudySession refreshed = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        assertThat(refreshed.getLastAccessedAt()).isEqualTo(accessedAt);
        assertThat(refreshed.getExpiresAt()).isEqualTo(accessedAt.plusDays(30));
    }

    @Test
    @DisplayName("세션을 두 번 생성하면 서로 다른 코드가 발급된다")
    void createsDistinctSessionCodes() {
        String first = (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");
        String second = (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("존재하지 않는 코드로 조회하면 404 SESSION_NOT_FOUND")
    void returns404ForUnknownCode() {
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange("/api/sessions/ZZZZZZZZ", HttpMethod.GET, null, JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("SESSION_NOT_FOUND");
        assertThat(response.getBody().get("message")).isEqualTo("유효한 학습 세션을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("형식이 잘못된 코드로 조회하면 400 INVALID_SESSION_CODE")
    void returns400ForMalformedCode() {
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange("/api/sessions/ABC", HttpMethod.GET, null, JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_SESSION_CODE");
        assertThat(response.getBody().get("message")).isEqualTo("올바르지 않은 세션 코드입니다.");
    }

    @Test
    @DisplayName("시험 정보를 등록하면 남은 실제 시간에 맞춰 학습시간이 초기화된다")
    void registerExamInfoAndRestore() {
        // 현재 2026-08-27 18:00, 시험 22:00 → 시험까지 240분. 사용자는 360분을 입력한다.
        LocalDateTime now = LocalDateTime.of(2026, 8, 27, 18, 0);
        testClock().setNow(now);

        String sessionCode = (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");

        ResponseEntity<Map<String, Object>> exam = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/exam",
                HttpMethod.PUT,
                jsonRequest("운영체제", "2026-08-27T22:00:00", 360),
                JSON_MAP);

        assertThat(exam.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exam.getBody().get("subject")).isEqualTo("운영체제");
        assertThat(exam.getBody().get("examAt")).isEqualTo("2026-08-27T22:00:00");
        assertThat(exam.getBody().get("availableStudyMinutes")).isEqualTo(360);
        assertThat(exam.getBody().get("remainingStudyMinutes")).isEqualTo(240);
        assertThat(exam.getBody().get("status")).isEqualTo("CREATED");

        // 세션 조회에도 시험 정보가 그대로 담긴다 (별도 조회 API를 만들지 않는다)
        ResponseEntity<Map<String, Object>> found = restTemplate.exchange(
                "/api/sessions/" + sessionCode, HttpMethod.GET, null, JSON_MAP);

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().get("subject")).isEqualTo("운영체제");
        assertThat(found.getBody().get("examAt")).isEqualTo("2026-08-27T22:00:00");
        assertThat(found.getBody().get("availableStudyMinutes")).isEqualTo(360);
        assertThat(found.getBody().get("remainingStudyMinutes")).isEqualTo(240);

        // DB에도 반영된다
        StudySession stored = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        assertThat(stored.getAvailableStudyMinutes()).isEqualTo(360);
        assertThat(stored.getRemainingStudyMinutes()).isEqualTo(240);
        assertThat(stored.hasExamInfo()).isTrue();
    }

    @Test
    @DisplayName("시험 정보를 다시 등록하면 두 시간 값이 다시 계산된다")
    void updateExamInfoRecalculates() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 27, 18, 0);
        testClock().setNow(now);

        String sessionCode = (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");

        restTemplate.exchange("/api/sessions/" + sessionCode + "/exam", HttpMethod.PUT,
                jsonRequest("운영체제", "2026-08-27T22:00:00", 360), JSON_MAP);

        ResponseEntity<Map<String, Object>> updated = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/exam",
                HttpMethod.PUT,
                jsonRequest("데이터베이스", "2026-08-29T10:00:00", 420),
                JSON_MAP);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("subject")).isEqualTo("데이터베이스");
        assertThat(updated.getBody().get("availableStudyMinutes")).isEqualTo(420);
        assertThat(updated.getBody().get("remainingStudyMinutes")).isEqualTo(420);
    }

    @Test
    @DisplayName("시험 정보 등록도 접근으로 간주해 보관 기한이 연장된다")
    void registeringExamInfoRefreshesAccessTime() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 27, 18, 0);
        testClock().setNow(createdAt);

        String sessionCode = (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");

        LocalDateTime accessedAt = createdAt.plusDays(5);
        testClock().setNow(accessedAt);

        restTemplate.exchange("/api/sessions/" + sessionCode + "/exam", HttpMethod.PUT,
                jsonRequest("운영체제", "2026-09-10T10:00:00", 360), JSON_MAP);

        StudySession stored = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        assertThat(stored.getLastAccessedAt()).isEqualTo(accessedAt);
        assertThat(stored.getExpiresAt()).isEqualTo(accessedAt.plusDays(30));
    }

    @Test
    @DisplayName("시험 시각이 과거면 400 INVALID_EXAM_TIME")
    void returns400ForPastExamTime() {
        testClock().setNow(LocalDateTime.of(2026, 8, 27, 18, 0));

        String sessionCode = (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/exam",
                HttpMethod.PUT,
                jsonRequest("운영체제", "2026-08-27T15:00:00", 360),
                JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_EXAM_TIME");
        assertThat(response.getBody().get("message")).isEqualTo("시험 시간은 현재 시간보다 이후여야 합니다.");
    }

    @Test
    @DisplayName("학습 가능 시간이 0이면 400 INVALID_REQUEST")
    void returns400ForZeroStudyMinutes() {
        testClock().setNow(LocalDateTime.of(2026, 8, 27, 18, 0));

        String sessionCode = (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/exam",
                HttpMethod.PUT,
                jsonRequest("운영체제", "2026-08-28T10:00:00", 0),
                JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("존재하지 않는 세션에 시험 정보를 등록하면 404")
    void returns404WhenRegisteringExamInfoForUnknownSession() {
        testClock().setNow(LocalDateTime.of(2026, 8, 27, 18, 0));

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/sessions/ZZZZZZZZ/exam",
                HttpMethod.PUT,
                jsonRequest("운영체제", "2026-08-28T10:00:00", 360),
                JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("SESSION_NOT_FOUND");
    }

    @Test
    @DisplayName("혼동 문자(0, O, 1, I, L)가 포함된 코드도 400으로 거부한다")
    void returns400ForCodeWithConfusingCharacters() {
        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange("/api/sessions/0K2M9QXF", HttpMethod.GET, null, JSON_MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_SESSION_CODE");
    }
}
