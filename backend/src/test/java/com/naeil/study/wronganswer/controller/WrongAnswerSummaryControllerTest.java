package com.naeil.study.wronganswer.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naeil.study.quiz.exception.QuizNotFoundException;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.wronganswer.entity.ReviewPriority;
import com.naeil.study.wronganswer.entity.TopicReviewSnapshot;
import com.naeil.study.wronganswer.entity.WrongAnswerSummary;
import com.naeil.study.wronganswer.exception.QuizNotCompletedException;
import com.naeil.study.wronganswer.exception.WrongAnswerSummaryGenerationFailedException;
import com.naeil.study.wronganswer.exception.WrongAnswerSummaryNotFoundException;
import com.naeil.study.wronganswer.service.WrongAnswerSummaryService;
import com.naeil.study.wronganswer.service.WrongAnswerSummaryService.SummaryOutcome;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WrongAnswerSummaryController.class)
@DisplayName("WrongAnswerSummaryController - 오답 복습 요약 API")
class WrongAnswerSummaryControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 22, 0, 0);
    private static final String SESSION_CODE = "7K2M9QXF";
    private static final UUID TOPIC_ID = UUID.fromString("77777777-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WrongAnswerSummaryService summaryService;

    private String url() {
        return "/api/sessions/" + SESSION_CODE + "/wrong-answer-summary";
    }

    private WrongAnswerSummary summary() {
        StudySession session = StudySession.create(SESSION_CODE, NOW.minusHours(3), 30L);
        return WrongAnswerSummary.create(session, 2, "CPU 스케줄링을 우선 복습하세요.",
                List.of(new TopicReviewSnapshot(TOPIC_ID, "CPU 스케줄링",
                        List.of("Round Robin", "Time Quantum"),
                        "Round Robin 은 선점형 스케줄링이다.",
                        List.of("Time Quantum 이 크면 FCFS 와 비슷해진다."),
                        ReviewPriority.VERY_HIGH)),
                NOW.minusMinutes(10), NOW);
    }

    @Test
    @DisplayName("POST 새로 생성하면 → 201 요약 본문")
    void generatesSummary() throws Exception {
        given(summaryService.generate(anyString()))
                .willReturn(new SummaryOutcome(true, summary(), true));

        mockMvc.perform(post(url()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasWrongAnswers").value(true))
                .andExpect(jsonPath("$.wrongAnswerCount").value(2))
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.overallSummary").value("CPU 스케줄링을 우선 복습하세요."))
                .andExpect(jsonPath("$.topics", hasSize(1)))
                .andExpect(jsonPath("$.topics[0].topicId").value(TOPIC_ID.toString()))
                .andExpect(jsonPath("$.topics[0].topicTitle").value("CPU 스케줄링"))
                .andExpect(jsonPath("$.topics[0].wrongConcepts", hasSize(2)))
                .andExpect(jsonPath("$.topics[0].keyReviewPoints", hasSize(1)))
                .andExpect(jsonPath("$.topics[0].priority").value("VERY_HIGH"));
    }

    @Test
    @DisplayName("POST 캐시된 요약을 돌려주면 → 200")
    void returnsCachedSummary() throws Exception {
        given(summaryService.generate(anyString()))
                .willReturn(new SummaryOutcome(true, summary(), false));

        mockMvc.perform(post(url()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasWrongAnswers").value(true));
    }

    @Test
    @DisplayName("POST 오답이 없으면 → 200 빈 요약, 오류가 아니다")
    void reportsNoWrongAnswers() throws Exception {
        given(summaryService.generate(anyString()))
                .willReturn(new SummaryOutcome(false, null, false));

        mockMvc.perform(post(url()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasWrongAnswers").value(false))
                .andExpect(jsonPath("$.wrongAnswerCount").value(0))
                .andExpect(jsonPath("$.generatedAt").value(nullValue()))
                .andExpect(jsonPath("$.overallSummary").value(nullValue()))
                .andExpect(jsonPath("$.topics", hasSize(0)));
    }

    @Test
    @DisplayName("GET → 200 저장된 요약")
    void findsSummary() throws Exception {
        given(summaryService.find(anyString()))
                .willReturn(new SummaryOutcome(true, summary(), false));

        mockMvc.perform(get(url()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics[0].summary").value("Round Robin 은 선점형 스케줄링이다."));
    }

    @Test
    @DisplayName("아직 생성 전 조회 → 404 WRONG_ANSWER_SUMMARY_NOT_FOUND")
    void summaryNotFound() throws Exception {
        willThrow(new WrongAnswerSummaryNotFoundException())
                .given(summaryService).find(anyString());

        mockMvc.perform(get(url()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WRONG_ANSWER_SUMMARY_NOT_FOUND"));
    }

    @Test
    @DisplayName("퀴즈 미완료 → 409 QUIZ_NOT_COMPLETED")
    void quizNotCompleted() throws Exception {
        willThrow(new QuizNotCompletedException()).given(summaryService).generate(anyString());

        mockMvc.perform(post(url()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUIZ_NOT_COMPLETED"));
    }

    @Test
    @DisplayName("퀴즈 없음 → 404 QUIZ_NOT_FOUND")
    void quizNotFound() throws Exception {
        willThrow(new QuizNotFoundException()).given(summaryService).generate(anyString());

        mockMvc.perform(post(url()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUIZ_NOT_FOUND"));
    }

    @Test
    @DisplayName("AI 생성 실패 → 502 WRONG_ANSWER_SUMMARY_GENERATION_FAILED")
    void generationFailed() throws Exception {
        willThrow(new WrongAnswerSummaryGenerationFailedException("ai failed"))
                .given(summaryService).generate(anyString());

        mockMvc.perform(post(url()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("WRONG_ANSWER_SUMMARY_GENERATION_FAILED"));
    }
}
