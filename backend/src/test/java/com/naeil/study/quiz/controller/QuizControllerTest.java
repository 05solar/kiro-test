package com.naeil.study.quiz.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.quiz.entity.QuizDifficulty;
import com.naeil.study.quiz.entity.QuizResult;
import com.naeil.study.quiz.exception.NoQuizSourceContextException;
import com.naeil.study.quiz.exception.QuizGenerationFailedException;
import com.naeil.study.quiz.exception.QuizNotFoundException;
import com.naeil.study.quiz.exception.TopicStudyNotCompletedException;
import com.naeil.study.quiz.service.QuizAnswerService;
import com.naeil.study.quiz.service.QuizAnswerService.TopicQuizResults;
import com.naeil.study.quiz.service.QuizGenerationService;
import com.naeil.study.quiz.service.QuizGenerationService.QuizGenerationResult;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import com.naeil.study.topic.exception.TopicNotFoundException;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuizController.class)
@DisplayName("QuizController - 퀴즈 생성/조회/결과 API")
class QuizControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 21, 0, 0);
    private static final String SESSION_CODE = "7K2M9QXF";
    private static final UUID TOPIC_ID = UUID.fromString("55555555-0000-4000-8000-000000000001");
    private static final UUID QUIZ_ID = UUID.fromString("55555555-0000-4000-8000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuizGenerationService quizGenerationService;

    @MockitoBean
    private QuizAnswerService quizAnswerService;

    private Topic topic;
    private Quiz quiz;

    @BeforeEach
    void setUp() throws Exception {
        StudySession session = StudySession.create(SESSION_CODE, NOW, 30L);
        topic = Topic.create(session, "CPU 스케줄링", "요약", List.of("Round Robin"),
                TopicImportance.HIGH, 40, false, false, false, false, List.of(), 1, NOW);
        setId(Topic.class, topic, TOPIC_ID);
        quiz = Quiz.create(topic, 1, 1, "Round Robin 의 특징은?", List.of("A", "B", "C", "D"),
                2, "해설이다", QuizDifficulty.MEDIUM, List.of(), NOW);
        setId(Quiz.class, quiz, QUIZ_ID);
    }

    private void setId(Class<?> type, Object target, UUID id) throws Exception {
        Field field = type.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    private String quizzesUrl() {
        return "/api/sessions/" + SESSION_CODE + "/topics/" + TOPIC_ID + "/quizzes";
    }

    private String resultsUrl() {
        return "/api/sessions/" + SESSION_CODE + "/topics/" + TOPIC_ID + "/quiz-results";
    }

    @Test
    @DisplayName("POST 새로 생성하면 → 201, 정답과 해설은 응답에 없다")
    void generatesQuizzes() throws Exception {
        given(quizGenerationService.generate(anyString(), any(UUID.class)))
                .willReturn(new QuizGenerationResult(topic, List.of(quiz), true));

        mockMvc.perform(post(quizzesUrl()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.topicId").value(TOPIC_ID.toString()))
                .andExpect(jsonPath("$.topicTitle").value("CPU 스케줄링"))
                .andExpect(jsonPath("$.quizzes", hasSize(1)))
                .andExpect(jsonPath("$.quizzes[0].id").value(QUIZ_ID.toString()))
                .andExpect(jsonPath("$.quizzes[0].question").value("Round Robin 의 특징은?"))
                .andExpect(jsonPath("$.quizzes[0].options", hasSize(4)))
                .andExpect(jsonPath("$.quizzes[0].difficulty").value("MEDIUM"))
                // 정답 정보는 채점 전에 절대 내보내지 않는다
                .andExpect(jsonPath("$.quizzes[0].correctIndex").doesNotExist())
                .andExpect(jsonPath("$.quizzes[0].explanation").doesNotExist());
    }

    @Test
    @DisplayName("POST 기존 퀴즈를 돌려주면 → 200")
    void returnsExistingQuizzes() throws Exception {
        given(quizGenerationService.generate(anyString(), any(UUID.class)))
                .willReturn(new QuizGenerationResult(topic, List.of(quiz), false));

        mockMvc.perform(post(quizzesUrl()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET → 200, 정답과 해설은 역시 없다")
    void findsQuizzes() throws Exception {
        given(quizGenerationService.find(anyString(), any(UUID.class)))
                .willReturn(new QuizGenerationResult(topic, List.of(quiz), false));

        mockMvc.perform(get(quizzesUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quizzes[0].correctIndex").doesNotExist())
                .andExpect(jsonPath("$.quizzes[0].explanation").doesNotExist());
    }

    @Test
    @DisplayName("GET quiz-results → 200 집계")
    void aggregatesResults() throws Exception {
        StudySession session = StudySession.create(SESSION_CODE, NOW, 30L);
        QuizResult result = QuizResult.create(session, quiz, 2, true, NOW);
        given(quizAnswerService.results(anyString(), any(UUID.class)))
                .willReturn(new TopicQuizResults(topic, 5, List.of(result)));

        mockMvc.perform(get(resultsUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicId").value(TOPIC_ID.toString()))
                .andExpect(jsonPath("$.totalQuestions").value(5))
                .andExpect(jsonPath("$.answeredQuestions").value(1))
                .andExpect(jsonPath("$.correctAnswers").value(1))
                .andExpect(jsonPath("$.scorePercentage").value(20))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].quizId").value(QUIZ_ID.toString()))
                .andExpect(jsonPath("$.results[0].correct").value(true));
    }

    @Test
    @DisplayName("다른 세션의 Topic → 404 TOPIC_NOT_FOUND")
    void topicNotFound() throws Exception {
        willThrow(new TopicNotFoundException())
                .given(quizGenerationService).generate(anyString(), any(UUID.class));

        mockMvc.perform(post(quizzesUrl()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOPIC_NOT_FOUND"));
    }

    @Test
    @DisplayName("학습 미완료 → 409 TOPIC_STUDY_NOT_COMPLETED")
    void studyNotCompleted() throws Exception {
        willThrow(new TopicStudyNotCompletedException())
                .given(quizGenerationService).generate(anyString(), any(UUID.class));

        mockMvc.perform(post(quizzesUrl()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOPIC_STUDY_NOT_COMPLETED"));
    }

    @Test
    @DisplayName("AI 생성 실패 → 502 QUIZ_GENERATION_FAILED")
    void generationFailed() throws Exception {
        willThrow(new QuizGenerationFailedException("ai failed"))
                .given(quizGenerationService).generate(anyString(), any(UUID.class));

        mockMvc.perform(post(quizzesUrl()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("QUIZ_GENERATION_FAILED"));
    }

    @Test
    @DisplayName("근거 자료 없음 → 400 NO_QUIZ_SOURCE_CONTEXT")
    void noSourceContext() throws Exception {
        willThrow(new NoQuizSourceContextException())
                .given(quizGenerationService).generate(anyString(), any(UUID.class));

        mockMvc.perform(post(quizzesUrl()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_QUIZ_SOURCE_CONTEXT"));
    }

    @Test
    @DisplayName("아직 생성하지 않은 퀴즈 조회 → 404 QUIZ_NOT_FOUND")
    void quizNotFound() throws Exception {
        willThrow(new QuizNotFoundException())
                .given(quizGenerationService).find(anyString(), any(UUID.class));

        mockMvc.perform(get(quizzesUrl()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUIZ_NOT_FOUND"));
    }

    @Test
    @DisplayName("topicId 형식이 잘못되면 → 400 INVALID_REQUEST")
    void invalidTopicId() throws Exception {
        mockMvc.perform(post("/api/sessions/" + SESSION_CODE + "/topics/not-a-uuid/quizzes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
