package com.naeil.study.studycontext.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.exception.InvalidSessionCodeException;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.studycontext.dto.UpdateStudyContextRequest;
import com.naeil.study.studycontext.entity.StudyContext;
import com.naeil.study.studycontext.service.StudyContextService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StudyContextController.class)
@DisplayName("StudyContextController - 학습 맥락 API")
class StudyContextControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 17, 30, 0);
    private static final String SESSION_CODE = "7K2M9QXF";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyContextService studyContextService;

    private StudyContext studyContext(String emphasis, String pastExam, String weak, String must) {
        StudySession session = StudySession.create(SESSION_CODE, NOW, 30L);
        return StudyContext.create(session, emphasis, pastExam, weak, must, NOW);
    }

    private String body(String emphasis, String pastExam, String weak, String must) {
        return """
                {
                  "professorEmphasis": %s,
                  "pastExamInfo": %s,
                  "weakAreas": %s,
                  "mustStudyAreas": %s
                }
                """.formatted(quote(emphasis), quote(pastExam), quote(weak), quote(must));
    }

    private String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    @Test
    @DisplayName("PUT 정상 → 200, 저장된 학습 맥락을 반환한다")
    void upsertReturns200() throws Exception {
        given(studyContextService.upsert(eq(SESSION_CODE), any(UpdateStudyContextRequest.class)))
                .willReturn(studyContext("교착상태 조건 강조", "CPU Scheduling 계산 문제 기출",
                        "가상 메모리와 페이지 교체 알고리즘", "교착상태와 CPU Scheduling"));

        mockMvc.perform(put("/api/sessions/{sessionCode}/study-context", SESSION_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("교착상태 조건 강조", "CPU Scheduling 계산 문제 기출",
                                "가상 메모리와 페이지 교체 알고리즘", "교착상태와 CPU Scheduling")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(SESSION_CODE))
                .andExpect(jsonPath("$.professorEmphasis").value("교착상태 조건 강조"))
                .andExpect(jsonPath("$.pastExamInfo").value("CPU Scheduling 계산 문제 기출"))
                .andExpect(jsonPath("$.weakAreas").value("가상 메모리와 페이지 교체 알고리즘"))
                .andExpect(jsonPath("$.mustStudyAreas").value("교착상태와 CPU Scheduling"))
                .andExpect(jsonPath("$.updatedAt").value("2026-08-27T17:30:00"));
    }

    @Test
    @DisplayName("PUT 수정 → 200, 바뀐 값을 반환한다")
    void upsertUpdateReturns200() throws Exception {
        given(studyContextService.upsert(eq(SESSION_CODE), any(UpdateStudyContextRequest.class)))
                .willReturn(studyContext(null, null, "교착상태", null));

        mockMvc.perform(put("/api/sessions/{sessionCode}/study-context", SESSION_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, null, "교착상태", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weakAreas").value("교착상태"))
                .andExpect(jsonPath("$.professorEmphasis").value(nullValue()));
    }

    @Test
    @DisplayName("PUT 모든 값 null → 200")
    void upsertWithAllNullReturns200() throws Exception {
        given(studyContextService.upsert(eq(SESSION_CODE), any(UpdateStudyContextRequest.class)))
                .willReturn(studyContext(null, null, null, null));

        mockMvc.perform(put("/api/sessions/{sessionCode}/study-context", SESSION_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professorEmphasis").value(nullValue()))
                .andExpect(jsonPath("$.pastExamInfo").value(nullValue()))
                .andExpect(jsonPath("$.weakAreas").value(nullValue()))
                .andExpect(jsonPath("$.mustStudyAreas").value(nullValue()));
    }

    @Test
    @DisplayName("PUT 2000자 초과 → 400 INVALID_REQUEST, 서비스를 호출하지 않는다")
    void upsertReturns400WhenFieldTooLong() throws Exception {
        String tooLong = "가".repeat(2001);

        mockMvc.perform(put("/api/sessions/{sessionCode}/study-context", SESSION_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(tooLong, null, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("교수님 강조 내용은 2000자 이하로 입력해 주세요."));

        verifyNoInteractions(studyContextService);
    }

    @Test
    @DisplayName("PUT 정확히 2000자 → 200")
    void upsertAcceptsExactlyMaxLength() throws Exception {
        String exact = "가".repeat(2000);
        given(studyContextService.upsert(eq(SESSION_CODE), any(UpdateStudyContextRequest.class)))
                .willReturn(studyContext(exact, null, null, null));

        mockMvc.perform(put("/api/sessions/{sessionCode}/study-context", SESSION_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(exact, null, null, null)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT 존재하지 않는 세션 → 404 SESSION_NOT_FOUND")
    void upsertReturns404() throws Exception {
        willThrow(new SessionNotFoundException())
                .given(studyContextService).upsert(anyString(), any(UpdateStudyContextRequest.class));

        mockMvc.perform(put("/api/sessions/{sessionCode}/study-context", "ZZZZZZZZ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("강조", null, null, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT 코드 형식 오류 → 400 INVALID_SESSION_CODE")
    void upsertReturns400WhenSessionCodeInvalid() throws Exception {
        willThrow(new InvalidSessionCodeException())
                .given(studyContextService).upsert(anyString(), any(UpdateStudyContextRequest.class));

        mockMvc.perform(put("/api/sessions/{sessionCode}/study-context", "ABC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("강조", null, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION_CODE"));
    }

    @Test
    @DisplayName("GET 존재 → 200, 저장된 값을 반환한다")
    void findReturns200() throws Exception {
        given(studyContextService.find(SESSION_CODE))
                .willReturn(Optional.of(studyContext("교착상태 조건 강조", null, "가상 메모리", null)));

        mockMvc.perform(get("/api/sessions/{sessionCode}/study-context", SESSION_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(SESSION_CODE))
                .andExpect(jsonPath("$.professorEmphasis").value("교착상태 조건 강조"))
                .andExpect(jsonPath("$.pastExamInfo").value(nullValue()))
                .andExpect(jsonPath("$.weakAreas").value("가상 메모리"))
                .andExpect(jsonPath("$.mustStudyAreas").value(nullValue()));
    }

    @Test
    @DisplayName("GET 미입력 → 200 + 모든 값 null (404가 아니다)")
    void findReturns200WithNullFieldsWhenAbsent() throws Exception {
        given(studyContextService.find(SESSION_CODE)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/sessions/{sessionCode}/study-context", SESSION_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(SESSION_CODE))
                .andExpect(jsonPath("$.professorEmphasis").value(nullValue()))
                .andExpect(jsonPath("$.pastExamInfo").value(nullValue()))
                .andExpect(jsonPath("$.weakAreas").value(nullValue()))
                .andExpect(jsonPath("$.mustStudyAreas").value(nullValue()))
                .andExpect(jsonPath("$.updatedAt").value(nullValue()));
    }

    @Test
    @DisplayName("GET 존재하지 않는 세션 → 404 SESSION_NOT_FOUND")
    void findReturns404() throws Exception {
        willThrow(new SessionNotFoundException()).given(studyContextService).find(anyString());

        mockMvc.perform(get("/api/sessions/{sessionCode}/study-context", "ZZZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }
}
