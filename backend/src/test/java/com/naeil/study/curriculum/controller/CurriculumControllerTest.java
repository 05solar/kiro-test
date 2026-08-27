package com.naeil.study.curriculum.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naeil.study.curriculum.entity.Curriculum;
import com.naeil.study.curriculum.entity.PriorityReason;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.exception.CurriculumGenerationFailedException;
import com.naeil.study.curriculum.exception.CurriculumNotFoundException;
import com.naeil.study.curriculum.exception.NoStudyTimeAvailableException;
import com.naeil.study.curriculum.exception.SessionNotReadyException;
import com.naeil.study.curriculum.exception.TopicsRequiredException;
import com.naeil.study.curriculum.service.CurriculumService;
import com.naeil.study.curriculum.service.CurriculumService.CurriculumResult;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CurriculumController.class)
@DisplayName("CurriculumController - 학습 계획 API")
class CurriculumControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 18, 0, 0);
    private static final String SESSION_CODE = "7K2M9QXF";
    private static final UUID CURRICULUM_ID = UUID.fromString("33333333-0000-4000-8000-000000000001");
    private static final UUID STEP_ID = UUID.fromString("44444444-0000-4000-8000-000000000001");
    private static final UUID TOPIC_ID = UUID.fromString("22222222-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurriculumService curriculumService;

    private void setId(Class<?> type, Object target, UUID id) throws Exception {
        Field field = type.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    private CurriculumResult result(boolean created) throws Exception {
        StudySession session = StudySession.create(SESSION_CODE, NOW, 30L);
        Curriculum curriculum = Curriculum.create(session, 180, 175, NOW);
        setId(Curriculum.class, curriculum, CURRICULUM_ID);

        Topic topic = Topic.create(session, "CPU 스케줄링", "요약", List.of("FCFS"),
                TopicImportance.VERY_HIGH, 50, false, true, false, true, List.of(), 1, NOW);
        setId(Topic.class, topic, TOPIC_ID);

        StudyStep studyStep = StudyStep.study(curriculum, topic, 1, "CPU 스케줄링", 40, 50, true,
                List.of(PriorityReason.MUST_STUDY, PriorityReason.CORE_TOPIC), NOW);
        setId(StudyStep.class, studyStep, STEP_ID);

        StudyStep reviewStep = StudyStep.review(curriculum, 2, "핵심 개념 최종 복습", 135, NOW);
        setId(StudyStep.class, reviewStep, UUID.randomUUID());

        return new CurriculumResult(curriculum, List.of(studyStep, reviewStep), created);
    }

    @Test
    @DisplayName("POST /curriculum 새로 생성 → 201")
    void createReturns201() throws Exception {
        given(curriculumService.create(SESSION_CODE)).willReturn(result(true));

        mockMvc.perform(post("/api/sessions/{sessionCode}/curriculum", SESSION_CODE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.curriculumId").value(CURRICULUM_ID.toString()))
                .andExpect(jsonPath("$.initialRemainingMinutes").value(180))
                .andExpect(jsonPath("$.totalAllocatedMinutes").value(175))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].id").value(STEP_ID.toString()))
                .andExpect(jsonPath("$.steps[0].order").value(1))
                .andExpect(jsonPath("$.steps[0].type").value("STUDY"))
                .andExpect(jsonPath("$.steps[0].topicId").value(TOPIC_ID.toString()))
                .andExpect(jsonPath("$.steps[0].title").value("CPU 스케줄링"))
                .andExpect(jsonPath("$.steps[0].importance").value("VERY_HIGH"))
                .andExpect(jsonPath("$.steps[0].originalEstimatedMinutes").value(50))
                .andExpect(jsonPath("$.steps[0].allocatedMinutes").value(40))
                .andExpect(jsonPath("$.steps[0].mandatory").value(true))
                .andExpect(jsonPath("$.steps[0].priorityReasons[0]").value("MUST_STUDY"))
                .andExpect(jsonPath("$.steps[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /curriculum 이미 있으면 → 200, 기존 계획을 돌려준다")
    void createReturns200WhenAlreadyExists() throws Exception {
        given(curriculumService.create(SESSION_CODE)).willReturn(result(false));

        mockMvc.perform(post("/api/sessions/{sessionCode}/curriculum", SESSION_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.curriculumId").value(CURRICULUM_ID.toString()));
    }

    @Test
    @DisplayName("복습 단계는 topicId와 importance가 비어 있다")
    void reviewStepHasNoTopic() throws Exception {
        given(curriculumService.create(SESSION_CODE)).willReturn(result(true));

        mockMvc.perform(post("/api/sessions/{sessionCode}/curriculum", SESSION_CODE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.steps[1].type").value("REVIEW"))
                .andExpect(jsonPath("$.steps[1].topicId").value(nullValue()))
                .andExpect(jsonPath("$.steps[1].importance").value(nullValue()))
                .andExpect(jsonPath("$.steps[1].allocatedMinutes").value(135));
    }

    @Test
    @DisplayName("POST /curriculum 분석된 주제 없음 → 400 TOPICS_REQUIRED")
    void createReturns400WithoutTopics() throws Exception {
        willThrow(new TopicsRequiredException()).given(curriculumService).create(anyString());

        mockMvc.perform(post("/api/sessions/{sessionCode}/curriculum", SESSION_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOPICS_REQUIRED"));
    }

    @Test
    @DisplayName("POST /curriculum 분석 미완료 → 400 SESSION_NOT_READY")
    void createReturns400WhenSessionNotReady() throws Exception {
        willThrow(new SessionNotReadyException()).given(curriculumService).create(anyString());

        mockMvc.perform(post("/api/sessions/{sessionCode}/curriculum", SESSION_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_READY"));
    }

    @Test
    @DisplayName("POST /curriculum 남은 시간 없음 → 400 NO_STUDY_TIME_AVAILABLE")
    void createReturns400WithoutStudyTime() throws Exception {
        willThrow(new NoStudyTimeAvailableException()).given(curriculumService).create(anyString());

        mockMvc.perform(post("/api/sessions/{sessionCode}/curriculum", SESSION_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_STUDY_TIME_AVAILABLE"));
    }

    @Test
    @DisplayName("POST /curriculum 계획 생성 불가 → 422, 내부 원인을 노출하지 않는다")
    void createReturns422WhenGenerationFails() throws Exception {
        willThrow(new CurriculumGenerationFailedException("available minutes below minimum: 3"))
                .given(curriculumService).create(anyString());

        mockMvc.perform(post("/api/sessions/{sessionCode}/curriculum", SESSION_CODE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRICULUM_GENERATION_FAILED"))
                .andExpect(jsonPath("$.message").value("현재 남은 시간으로 학습 계획을 생성할 수 없습니다."));
    }

    @Test
    @DisplayName("POST /curriculum 존재하지 않는 세션 → 404")
    void createReturns404() throws Exception {
        willThrow(new SessionNotFoundException()).given(curriculumService).create(anyString());

        mockMvc.perform(post("/api/sessions/{sessionCode}/curriculum", "ZZZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /curriculum → 200")
    void findReturns200() throws Exception {
        given(curriculumService.find(SESSION_CODE)).willReturn(result(false));

        mockMvc.perform(get("/api/sessions/{sessionCode}/curriculum", SESSION_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.curriculumId").value(CURRICULUM_ID.toString()))
                .andExpect(jsonPath("$.steps.length()").value(2));
    }

    @Test
    @DisplayName("GET /curriculum 아직 만들지 않음 → 404 CURRICULUM_NOT_FOUND")
    void findReturns404WhenAbsent() throws Exception {
        willThrow(new CurriculumNotFoundException()).given(curriculumService).find(anyString());

        mockMvc.perform(get("/api/sessions/{sessionCode}/curriculum", SESSION_CODE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CURRICULUM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("학습 계획을 찾을 수 없습니다."));
    }
}
