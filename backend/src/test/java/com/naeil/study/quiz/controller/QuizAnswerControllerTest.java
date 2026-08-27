package com.naeil.study.quiz.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.quiz.entity.QuizDifficulty;
import com.naeil.study.quiz.entity.QuizResult;
import com.naeil.study.quiz.exception.InvalidQuizOptionException;
import com.naeil.study.quiz.exception.QuizNotFoundException;
import com.naeil.study.quiz.service.QuizAnswerService;
import com.naeil.study.quiz.service.QuizAnswerService.AnswerResult;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuizAnswerController.class)
@DisplayName("QuizAnswerController - 답안 제출 API")
class QuizAnswerControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 21, 0, 0);
    private static final String SESSION_CODE = "7K2M9QXF";
    private static final UUID QUIZ_ID = UUID.fromString("66666666-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuizAnswerService quizAnswerService;

    private Quiz quiz;
    private StudySession session;

    @BeforeEach
    void setUp() throws Exception {
        session = StudySession.create(SESSION_CODE, NOW, 30L);
        Topic topic = Topic.create(session, "CPU 스케줄링", "요약", List.of("Round Robin"),
                TopicImportance.HIGH, 40, false, false, false, false, List.of(), 1, NOW);
        quiz = Quiz.create(topic, 1, "문제", List.of("A", "B", "C", "D"),
                0, "Round Robin 은 선점형이다.", QuizDifficulty.MEDIUM, List.of(), NOW);
        setId(Quiz.class, quiz, QUIZ_ID);
    }

    private void setId(Class<?> type, Object target, UUID id) throws Exception {
        Field field = type.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    private String answerUrl() {
        return "/api/sessions/" + SESSION_CODE + "/quizzes/" + QUIZ_ID + "/answer";
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder answerRequest(String body) {
        return post(answerUrl())
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .content(body);
    }

    @Test
    @DisplayName("POST answer → 200, 채점 후 정답과 해설을 공개한다")
    void answersQuiz() throws Exception {
        QuizResult result = QuizResult.create(session, quiz, 2, false, NOW);
        given(quizAnswerService.answer(anyString(), any(UUID.class), anyInt()))
                .willReturn(new AnswerResult(quiz, result, false));

        mockMvc.perform(answerRequest("""
                        {"selectedIndex":2}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quizId").value(QUIZ_ID.toString()))
                .andExpect(jsonPath("$.selectedIndex").value(2))
                .andExpect(jsonPath("$.correctIndex").value(0))
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.explanation").value("Round Robin 은 선점형이다."))
                .andExpect(jsonPath("$.answeredAt").exists());
    }

    @Test
    @DisplayName("보기 번호 범위 오류 → 400 INVALID_QUIZ_OPTION")
    void invalidOption() throws Exception {
        willThrow(new InvalidQuizOptionException())
                .given(quizAnswerService).answer(anyString(), any(UUID.class), anyInt());

        mockMvc.perform(answerRequest("""
                        {"selectedIndex":9}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUIZ_OPTION"));
    }

    @Test
    @DisplayName("다른 세션의 퀴즈 → 404 QUIZ_NOT_FOUND")
    void quizNotFound() throws Exception {
        willThrow(new QuizNotFoundException())
                .given(quizAnswerService).answer(anyString(), any(UUID.class), anyInt());

        mockMvc.perform(answerRequest("""
                        {"selectedIndex":1}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUIZ_NOT_FOUND"));
    }

    @Test
    @DisplayName("selectedIndex 가 없으면 → 400 INVALID_REQUEST")
    void missingSelectedIndex() throws Exception {
        mockMvc.perform(answerRequest("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("본문이 없으면 → 400 INVALID_REQUEST")
    void missingBody() throws Exception {
        mockMvc.perform(post(answerUrl()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
