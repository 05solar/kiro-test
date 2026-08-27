package com.naeil.study.session.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naeil.study.session.dto.UpdateExamRequest;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.exception.InvalidExamTimeException;
import com.naeil.study.session.exception.InvalidSessionCodeException;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.session.service.SessionService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SessionController.class)
@DisplayName("SessionController - 세션 API")
class SessionControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 15, 30, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    @Test
    @DisplayName("POST /api/sessions → 201, 8자리 코드와 CREATED 상태를 반환한다")
    void createSessionReturns201() throws Exception {
        given(sessionService.createSession())
                .willReturn(StudySession.create("7K2M9QXF", NOW, 30L));

        mockMvc.perform(post("/api/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionCode").value("7K2M9QXF"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    @DisplayName("GET /api/sessions/{정상코드} → 200, 세션 정보를 반환한다")
    void getSessionReturns200() throws Exception {
        given(sessionService.getSessionAndTouch("7K2M9QXF"))
                .willReturn(StudySession.create("7K2M9QXF", NOW, 30L));

        mockMvc.perform(get("/api/sessions/{sessionCode}", "7K2M9QXF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value("7K2M9QXF"))
                .andExpect(jsonPath("$.subject").value(nullValue()))
                .andExpect(jsonPath("$.examAt").value(nullValue()))
                .andExpect(jsonPath("$.availableStudyMinutes").value(nullValue()))
                .andExpect(jsonPath("$.remainingStudyMinutes").value(nullValue()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.currentStepOrder").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").value("2026-08-27T15:30:00"))
                .andExpect(jsonPath("$.lastAccessedAt").value("2026-08-27T15:30:00"))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-26T15:30:00"))
                // 내부 식별자(UUID)는 응답에 포함하지 않는다.
                .andExpect(jsonPath("$.id").doesNotExist())
                // 아직 값이 없는 필드도 키 자체는 응답에 존재해야 한다.
                .andExpect(content().string(containsString("\"availableStudyMinutes\":null")))
                .andExpect(content().string(containsString("\"remainingStudyMinutes\":null")));
    }

    @Test
    @DisplayName("GET /api/sessions/{존재하지않는코드} → 404 SESSION_NOT_FOUND")
    void getSessionReturns404() throws Exception {
        willThrow(new SessionNotFoundException()).given(sessionService).getSessionAndTouch(anyString());

        mockMvc.perform(get("/api/sessions/{sessionCode}", "ZZZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("유효한 학습 세션을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("GET /api/sessions/ABC → 400 INVALID_SESSION_CODE")
    void getSessionReturns400WhenCodeFormatIsInvalid() throws Exception {
        willThrow(new InvalidSessionCodeException()).given(sessionService).getSessionAndTouch(anyString());

        mockMvc.perform(get("/api/sessions/{sessionCode}", "ABC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION_CODE"))
                .andExpect(jsonPath("$.message").value("올바르지 않은 세션 코드입니다."));
    }

    @Nested
    @DisplayName("PUT /api/sessions/{sessionCode}/exam")
    class UpdateExamInfo {

        private String body(String subject, String examAt, Object minutes) {
            return """
                    {
                      "subject": %s,
                      "examAt": %s,
                      "availableStudyMinutes": %s
                    }
                    """.formatted(
                    subject == null ? "null" : "\"" + subject + "\"",
                    examAt == null ? "null" : "\"" + examAt + "\"",
                    minutes);
        }

        private StudySession examRegisteredSession(int available, int remaining) {
            StudySession session = StudySession.create("7K2M9QXF", NOW, 30L);
            session.updateExamInfo("운영체제", LocalDateTime.of(2026, 8, 28, 10, 0), available, remaining, NOW);
            return session;
        }

        @Test
        @DisplayName("정상 요청 → 200, 저장된 시험 정보를 반환한다")
        void returns200() throws Exception {
            given(sessionService.updateExamInfo(eq("7K2M9QXF"), any(UpdateExamRequest.class)))
                    .willReturn(examRegisteredSession(360, 360));

            mockMvc.perform(put("/api/sessions/{sessionCode}/exam", "7K2M9QXF")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("운영체제", "2026-08-28T10:00:00", 360)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionCode").value("7K2M9QXF"))
                    .andExpect(jsonPath("$.subject").value("운영체제"))
                    .andExpect(jsonPath("$.examAt").value("2026-08-28T10:00:00"))
                    .andExpect(jsonPath("$.availableStudyMinutes").value(360))
                    .andExpect(jsonPath("$.remainingStudyMinutes").value(360))
                    .andExpect(jsonPath("$.status").value("CREATED"));
        }

        @Test
        @DisplayName("시험까지 남은 시간이 짧으면 remainingStudyMinutes가 줄어든 채로 반환된다")
        void returnsClampedRemainingMinutes() throws Exception {
            given(sessionService.updateExamInfo(eq("7K2M9QXF"), any(UpdateExamRequest.class)))
                    .willReturn(examRegisteredSession(360, 240));

            mockMvc.perform(put("/api/sessions/{sessionCode}/exam", "7K2M9QXF")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("운영체제", "2026-08-27T22:00:00", 360)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableStudyMinutes").value(360))
                    .andExpect(jsonPath("$.remainingStudyMinutes").value(240));
        }

        @Test
        @DisplayName("시험 시각이 과거 → 400 INVALID_EXAM_TIME")
        void returns400WhenExamTimeIsInThePast() throws Exception {
            willThrow(new InvalidExamTimeException())
                    .given(sessionService).updateExamInfo(anyString(), any(UpdateExamRequest.class));

            mockMvc.perform(put("/api/sessions/{sessionCode}/exam", "7K2M9QXF")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("운영체제", "2020-01-01T10:00:00", 360)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_EXAM_TIME"))
                    .andExpect(jsonPath("$.message").value("시험 시간은 현재 시간보다 이후여야 합니다."));
        }

        @Test
        @DisplayName("학습시간 0 → 400 INVALID_REQUEST, 서비스를 호출하지 않는다")
        void returns400WhenStudyMinutesIsZero() throws Exception {
            mockMvc.perform(put("/api/sessions/{sessionCode}/exam", "7K2M9QXF")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("운영체제", "2026-08-28T10:00:00", 0)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value("공부 가능한 시간은 1분 이상이어야 합니다."));

            verifyNoInteractions(sessionService);
        }

        @Test
        @DisplayName("학습시간 상한(10080분) 초과 → 400 INVALID_REQUEST")
        void returns400WhenStudyMinutesExceedsLimit() throws Exception {
            mockMvc.perform(put("/api/sessions/{sessionCode}/exam", "7K2M9QXF")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("운영체제", "2026-08-28T10:00:00", 999999)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verifyNoInteractions(sessionService);
        }

        @Test
        @DisplayName("과목명이 공백뿐이면 → 400 INVALID_REQUEST")
        void returns400WhenSubjectIsBlank() throws Exception {
            mockMvc.perform(put("/api/sessions/{sessionCode}/exam", "7K2M9QXF")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("   ", "2026-08-28T10:00:00", 360)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value("과목명을 입력해 주세요."));

            verifyNoInteractions(sessionService);
        }

        @Test
        @DisplayName("과목명이 100자를 넘으면 → 400 INVALID_REQUEST")
        void returns400WhenSubjectIsTooLong() throws Exception {
            String tooLong = "가".repeat(101);

            mockMvc.perform(put("/api/sessions/{sessionCode}/exam", "7K2M9QXF")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(tooLong, "2026-08-28T10:00:00", 360)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verifyNoInteractions(sessionService);
        }

        @Test
        @DisplayName("시험 일시가 없으면 → 400 INVALID_REQUEST")
        void returns400WhenExamAtIsMissing() throws Exception {
            mockMvc.perform(put("/api/sessions/{sessionCode}/exam", "7K2M9QXF")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("운영체제", null, 360)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value("시험 일시를 입력해 주세요."));

            verifyNoInteractions(sessionService);
        }

        @Test
        @DisplayName("본문 형식이 잘못되면 → 400 INVALID_REQUEST")
        void returns400WhenBodyIsMalformed() throws Exception {
            mockMvc.perform(put("/api/sessions/{sessionCode}/exam", "7K2M9QXF")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"subject\": }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verifyNoInteractions(sessionService);
        }

        @Test
        @DisplayName("존재하지 않는 세션 → 404 SESSION_NOT_FOUND")
        void returns404() throws Exception {
            willThrow(new SessionNotFoundException())
                    .given(sessionService).updateExamInfo(anyString(), any(UpdateExamRequest.class));

            mockMvc.perform(put("/api/sessions/{sessionCode}/exam", "ZZZZZZZZ")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("운영체제", "2026-08-28T10:00:00", 360)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
        }

        @Test
        @DisplayName("코드 형식이 잘못된 세션 → 400 INVALID_SESSION_CODE")
        void returns400WhenSessionCodeFormatIsInvalid() throws Exception {
            willThrow(new InvalidSessionCodeException())
                    .given(sessionService).updateExamInfo(anyString(), any(UpdateExamRequest.class));

            mockMvc.perform(put("/api/sessions/{sessionCode}/exam", "ABC")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("운영체제", "2026-08-28T10:00:00", 360)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SESSION_CODE"));
        }
    }
}
