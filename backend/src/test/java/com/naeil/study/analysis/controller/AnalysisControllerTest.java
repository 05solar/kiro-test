package com.naeil.study.analysis.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naeil.study.analysis.exception.AiAnalysisException;
import com.naeil.study.analysis.exception.AnalysisAlreadyRunningException;
import com.naeil.study.analysis.exception.ExamInfoRequiredException;
import com.naeil.study.analysis.exception.NoParsedDocumentException;
import com.naeil.study.analysis.service.AnalysisService;
import com.naeil.study.session.exception.InvalidSessionCodeException;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.topic.entity.Topic;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalysisController.class)
@DisplayName("AnalysisController - AI 분석 API")
class AnalysisControllerTest {

    private static final String SESSION_CODE = "7K2M9QXF";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisService analysisService;

    @Test
    @DisplayName("POST /analysis 정상 → 200, READY 상태와 Topic 수를 반환한다")
    void analyzeReturns200() throws Exception {
        given(analysisService.analyze(SESSION_CODE))
                .willReturn(List.of(Mockito.mock(Topic.class), Mockito.mock(Topic.class)));

        mockMvc.perform(post("/api/sessions/{sessionCode}/analysis", SESSION_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value(SESSION_CODE))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.topicCount").value(2));
    }

    @Test
    @DisplayName("POST /analysis 시험 정보 없음 → 400 EXAM_INFO_REQUIRED")
    void analyzeReturns400WithoutExamInfo() throws Exception {
        willThrow(new ExamInfoRequiredException()).given(analysisService).analyze(anyString());

        mockMvc.perform(post("/api/sessions/{sessionCode}/analysis", SESSION_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXAM_INFO_REQUIRED"))
                .andExpect(jsonPath("$.message").value("시험 정보를 먼저 입력해 주세요."));
    }

    @Test
    @DisplayName("POST /analysis 분석할 자료 없음 → 400 NO_PARSED_DOCUMENT")
    void analyzeReturns400WithoutParsedDocument() throws Exception {
        willThrow(new NoParsedDocumentException()).given(analysisService).analyze(anyString());

        mockMvc.perform(post("/api/sessions/{sessionCode}/analysis", SESSION_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_PARSED_DOCUMENT"));
    }

    @Test
    @DisplayName("POST /analysis 이미 분석 중 → 409 ANALYSIS_ALREADY_RUNNING")
    void analyzeReturns409WhenAlreadyRunning() throws Exception {
        willThrow(new AnalysisAlreadyRunningException()).given(analysisService).analyze(anyString());

        mockMvc.perform(post("/api/sessions/{sessionCode}/analysis", SESSION_CODE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ANALYSIS_ALREADY_RUNNING"));
    }

    @Test
    @DisplayName("POST /analysis 분석 실패 → 502 ANALYSIS_FAILED, 내부 원인을 노출하지 않는다")
    void analyzeReturns502WhenAnalysisFails() throws Exception {
        willThrow(new AiAnalysisException("ai call failed: topic merge"))
                .given(analysisService).analyze(anyString());

        mockMvc.perform(post("/api/sessions/{sessionCode}/analysis", SESSION_CODE))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ANALYSIS_FAILED"))
                .andExpect(jsonPath("$.message").value("자료 분석에 실패했습니다. 다시 시도해 주세요."));
    }

    @Test
    @DisplayName("POST /analysis 존재하지 않는 세션 → 404")
    void analyzeReturns404() throws Exception {
        willThrow(new SessionNotFoundException()).given(analysisService).analyze(anyString());

        mockMvc.perform(post("/api/sessions/{sessionCode}/analysis", "ZZZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /analysis 코드 형식 오류 → 400 INVALID_SESSION_CODE")
    void analyzeReturns400ForInvalidCode() throws Exception {
        willThrow(new InvalidSessionCodeException()).given(analysisService).analyze(anyString());

        mockMvc.perform(post("/api/sessions/{sessionCode}/analysis", "ABC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION_CODE"));
    }
}
