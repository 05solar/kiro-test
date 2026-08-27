package com.naeil.study.studycontext;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.session.entity.SessionStatus;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.repository.StudySessionRepository;
import com.naeil.study.studycontext.entity.StudyContext;
import com.naeil.study.studycontext.repository.StudyContextRepository;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 5단계 완료 시나리오를 검증하는 통합 테스트.
 *
 * <pre>
 * 세션 생성 → 시험 정보 → 학습 맥락 입력 → 조회로 동일 정보 확인
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("학습 맥락 API 통합 - 저장/수정/조회")
class StudyContextApiIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {
            };

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StudyContextRepository studyContextRepository;

    @Autowired
    private StudySessionRepository studySessionRepository;

    /** 한글 본문이므로 Content-Type에 UTF-8을 명시한다. */
    private HttpEntity<String> jsonRequest(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        return new HttpEntity<>(body, headers);
    }

    private String createSessionWithExamInfo() {
        String sessionCode = (String) restTemplate
                .exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP).getBody().get("sessionCode");
        restTemplate.exchange("/api/sessions/" + sessionCode + "/exam", HttpMethod.PUT,
                jsonRequest("""
                        {"subject":"운영체제","examAt":"2030-01-01T10:00:00","availableStudyMinutes":360}
                        """), JSON_MAP);
        return sessionCode;
    }

    private ResponseEntity<Map<String, Object>> put(String sessionCode, String body) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/study-context",
                HttpMethod.PUT, jsonRequest(body), JSON_MAP);
    }

    private ResponseEntity<Map<String, Object>> get(String sessionCode) {
        return restTemplate.exchange("/api/sessions/" + sessionCode + "/study-context",
                HttpMethod.GET, null, JSON_MAP);
    }

    @Test
    @DisplayName("학습 맥락을 입력하고 같은 정보를 조회할 수 있다")
    void savesAndReadsStudyContext() {
        String sessionCode = createSessionWithExamInfo();

        ResponseEntity<Map<String, Object>> saved = put(sessionCode, """
                {
                  "professorEmphasis": "교착상태 조건 강조",
                  "pastExamInfo": "CPU Scheduling 계산 문제 기출",
                  "weakAreas": "Virtual Memory",
                  "mustStudyAreas": "Deadlock"
                }
                """);

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody().get("sessionCode")).isEqualTo(sessionCode);
        assertThat(saved.getBody().get("professorEmphasis")).isEqualTo("교착상태 조건 강조");
        assertThat(saved.getBody().get("pastExamInfo")).isEqualTo("CPU Scheduling 계산 문제 기출");
        assertThat(saved.getBody().get("weakAreas")).isEqualTo("Virtual Memory");
        assertThat(saved.getBody().get("mustStudyAreas")).isEqualTo("Deadlock");
        assertThat(saved.getBody().get("updatedAt")).isNotNull();

        ResponseEntity<Map<String, Object>> found = get(sessionCode);

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().get("professorEmphasis")).isEqualTo("교착상태 조건 강조");
        assertThat(found.getBody().get("pastExamInfo")).isEqualTo("CPU Scheduling 계산 문제 기출");
        assertThat(found.getBody().get("weakAreas")).isEqualTo("Virtual Memory");
        assertThat(found.getBody().get("mustStudyAreas")).isEqualTo("Deadlock");

        StudySession session = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        StudyContext stored = studyContextRepository.findByStudySessionId(session.getId()).orElseThrow();
        assertThat(stored.getMustStudyAreas()).isEqualTo("Deadlock");
    }

    @Test
    @DisplayName("다시 입력하면 새 행이 생기지 않고 기존 값이 바뀐다")
    void updateDoesNotCreateAnotherRow() {
        String sessionCode = createSessionWithExamInfo();
        put(sessionCode, """
                {"professorEmphasis":null,"pastExamInfo":null,"weakAreas":"가상 메모리","mustStudyAreas":null}
                """);
        StudySession session = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        var contextId = studyContextRepository.findByStudySessionId(session.getId()).orElseThrow().getId();

        ResponseEntity<Map<String, Object>> updated = put(sessionCode, """
                {"professorEmphasis":null,"pastExamInfo":null,"weakAreas":"교착상태","mustStudyAreas":null}
                """);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("weakAreas")).isEqualTo("교착상태");

        StudyContext stored = studyContextRepository.findByStudySessionId(session.getId()).orElseThrow();
        assertThat(stored.getId()).isEqualTo(contextId);
        assertThat(stored.getWeakAreas()).isEqualTo("교착상태");
    }

    @Test
    @DisplayName("모든 값을 null로 보내면 저장된 내용이 비워진다 (삭제 API를 대신한다)")
    void clearsAllFields() {
        String sessionCode = createSessionWithExamInfo();
        put(sessionCode, """
                {"professorEmphasis":"강조","pastExamInfo":"기출","weakAreas":"취약","mustStudyAreas":"필수"}
                """);

        ResponseEntity<Map<String, Object>> cleared = put(sessionCode, """
                {"professorEmphasis":null,"pastExamInfo":null,"weakAreas":null,"mustStudyAreas":null}
                """);

        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody().get("professorEmphasis")).isNull();
        assertThat(cleared.getBody().get("mustStudyAreas")).isNull();

        StudySession session = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        assertThat(studyContextRepository.findByStudySessionId(session.getId()).orElseThrow().isEmpty())
                .isTrue();
    }

    @Test
    @DisplayName("공백만 입력하면 null로 저장된다")
    void normalizesBlankToNull() {
        String sessionCode = createSessionWithExamInfo();

        ResponseEntity<Map<String, Object>> saved = put(sessionCode, """
                {"professorEmphasis":"   교착상태 강조   ","pastExamInfo":null,"weakAreas":"     ","mustStudyAreas":null}
                """);

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody().get("professorEmphasis")).isEqualTo("교착상태 강조");
        assertThat(saved.getBody().get("weakAreas")).isNull();
    }

    @Test
    @DisplayName("학습 맥락을 입력하지 않은 세션도 200과 null 값들을 돌려준다")
    void returnsNullFieldsWhenNotEntered() {
        String sessionCode = createSessionWithExamInfo();

        ResponseEntity<Map<String, Object>> found = get(sessionCode);

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().get("sessionCode")).isEqualTo(sessionCode);
        assertThat(found.getBody()).containsKey("professorEmphasis");
        assertThat(found.getBody().get("professorEmphasis")).isNull();
        assertThat(found.getBody().get("pastExamInfo")).isNull();
        assertThat(found.getBody().get("weakAreas")).isNull();
        assertThat(found.getBody().get("mustStudyAreas")).isNull();
        assertThat(found.getBody().get("updatedAt")).isNull();
    }

    @Test
    @DisplayName("2000자를 넘으면 400으로 거부한다")
    void rejectsTooLongField() {
        String sessionCode = createSessionWithExamInfo();
        String tooLong = "가".repeat(2001);

        ResponseEntity<Map<String, Object>> response = put(sessionCode, """
                {"professorEmphasis":"%s","pastExamInfo":null,"weakAreas":null,"mustStudyAreas":null}
                """.formatted(tooLong));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("존재하지 않는 세션이면 404")
    void returns404ForUnknownSession() {
        assertThat(put("ZZZZZZZZ", """
                {"professorEmphasis":"강조","pastExamInfo":null,"weakAreas":null,"mustStudyAreas":null}
                """).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("ZZZZZZZZ").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("다른 세션의 학습 맥락은 보이지 않는다")
    void doesNotLeakOtherSessionContext() {
        String ownerCode = createSessionWithExamInfo();
        String otherCode = createSessionWithExamInfo();
        put(ownerCode, """
                {"professorEmphasis":"남의 강조 내용","pastExamInfo":null,"weakAreas":null,"mustStudyAreas":null}
                """);

        ResponseEntity<Map<String, Object>> found = get(otherCode);

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().get("professorEmphasis")).isNull();
    }

    @Test
    @DisplayName("학습 맥락 입력은 세션 상태를 바꾸지 않고 보관 기한만 연장한다")
    void doesNotChangeSessionStatus() {
        String sessionCode = createSessionWithExamInfo();
        StudySession before = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        assertThat(before.getStatus()).isEqualTo(SessionStatus.CREATED);
        var accessedBefore = before.getLastAccessedAt();

        put(sessionCode, """
                {"professorEmphasis":"강조","pastExamInfo":null,"weakAreas":null,"mustStudyAreas":null}
                """);

        StudySession after = studySessionRepository.findBySessionCode(sessionCode).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SessionStatus.CREATED);
        assertThat(after.getLastAccessedAt()).isAfterOrEqualTo(accessedBefore);
        assertThat(after.getExpiresAt()).isEqualTo(after.getLastAccessedAt().plusDays(30));
    }
}
