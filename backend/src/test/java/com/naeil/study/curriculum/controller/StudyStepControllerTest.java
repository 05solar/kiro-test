package com.naeil.study.curriculum.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naeil.study.curriculum.entity.Curriculum;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.exception.AnotherStepInProgressException;
import com.naeil.study.curriculum.exception.CurriculumNotFoundException;
import com.naeil.study.curriculum.exception.ExamAlreadyStartedException;
import com.naeil.study.curriculum.exception.InvalidStudyStepOrderException;
import com.naeil.study.curriculum.exception.StudyStepAlreadyCompletedException;
import com.naeil.study.curriculum.exception.StudyStepNotFoundException;
import com.naeil.study.curriculum.exception.StudyStepNotStartedException;
import com.naeil.study.curriculum.service.StudyStepService;
import com.naeil.study.curriculum.service.StudyStepService.CompletionResult;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.exception.SessionNotFoundException;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StudyStepController.class)
@DisplayName("StudyStepController - 학습 진행 API")
class StudyStepControllerTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 27, 19, 0, 0);
    private static final String SESSION_CODE = "7K2M9QXF";
    private static final UUID STEP_ID = UUID.fromString("44444444-0000-4000-8000-000000000001");
    private static final UUID NEXT_STEP_ID = UUID.fromString("44444444-0000-4000-8000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyStepService studyStepService;

    private void setId(Class<?> type, Object target, UUID id) throws Exception {
        Field field = type.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    private Curriculum curriculum() {
        StudySession session = StudySession.create(SESSION_CODE, START, 30L);
        return Curriculum.create(session, 180, 180, START);
    }

    private StudyStep step(int order, String title, int allocatedMinutes, UUID id) throws Exception {
        StudyStep step = StudyStep.study(curriculum(), null, order, title,
                allocatedMinutes, allocatedMinutes, false, List.of(), START);
        setId(StudyStep.class, step, id);
        return step;
    }

    private String startUrl(UUID stepId) {
        return "/api/sessions/" + SESSION_CODE + "/steps/" + stepId + "/start";
    }

    private String completeUrl(UUID stepId) {
        return "/api/sessions/" + SESSION_CODE + "/steps/" + stepId + "/complete";
    }

    @Test
    @DisplayName("POST start → 200 진행 중 상태와 시작 시각")
    void startsStep() throws Exception {
        StudyStep step = step(2, "CPU 스케줄링", 40, STEP_ID);
        step.start(START);
        given(studyStepService.start(anyString(), any(UUID.class))).willReturn(step);

        mockMvc.perform(post(startUrl(STEP_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepId").value(STEP_ID.toString()))
                .andExpect(jsonPath("$.stepOrder").value(2))
                .andExpect(jsonPath("$.type").value("STUDY"))
                .andExpect(jsonPath("$.title").value("CPU 스케줄링"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.allocatedMinutes").value(40))
                .andExpect(jsonPath("$.startedAt").exists())
                .andExpect(jsonPath("$.completedAt").value(nullValue()))
                .andExpect(jsonPath("$.actualStudyMinutes").value(nullValue()));
    }

    @Test
    @DisplayName("POST complete → 200 완료 단계와 다음 단계")
    void completesStep() throws Exception {
        StudyStep completed = step(2, "CPU 스케줄링", 40, STEP_ID);
        completed.start(START);
        completed.complete(START.plusMinutes(52));
        StudyStep next = step(3, "교착상태", 35, NEXT_STEP_ID);
        given(studyStepService.complete(anyString(), any(UUID.class)))
                .willReturn(new CompletionResult(completed, Optional.of(next), false));

        mockMvc.perform(post(completeUrl(STEP_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedStep.stepId").value(STEP_ID.toString()))
                .andExpect(jsonPath("$.completedStep.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedStep.allocatedMinutes").value(40))
                .andExpect(jsonPath("$.completedStep.actualStudyMinutes").value(52))
                .andExpect(jsonPath("$.completedStep.completedAt").exists())
                .andExpect(jsonPath("$.nextStep.stepId").value(NEXT_STEP_ID.toString()))
                .andExpect(jsonPath("$.nextStep.stepOrder").value(3))
                .andExpect(jsonPath("$.nextStep.status").value("PENDING"))
                .andExpect(jsonPath("$.curriculumCompleted").value(false));
    }

    @Test
    @DisplayName("마지막 단계를 완료하면 nextStep 이 비고 계획 완료로 표시된다")
    void completesLastStep() throws Exception {
        StudyStep completed = step(5, "핵심 개념 최종 복습", 30, STEP_ID);
        completed.start(START);
        completed.complete(START.plusMinutes(30));
        given(studyStepService.complete(anyString(), any(UUID.class)))
                .willReturn(new CompletionResult(completed, Optional.empty(), true));

        mockMvc.perform(post(completeUrl(STEP_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextStep").value(nullValue()))
                .andExpect(jsonPath("$.curriculumCompleted").value(true));
    }

    @Test
    @DisplayName("다른 세션의 단계 → 404 STUDY_STEP_NOT_FOUND")
    void stepNotFound() throws Exception {
        willThrow(new StudyStepNotFoundException())
                .given(studyStepService).start(anyString(), any(UUID.class));

        mockMvc.perform(post(startUrl(STEP_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDY_STEP_NOT_FOUND"));
    }

    @Test
    @DisplayName("학습 계획 없음 → 404 CURRICULUM_NOT_FOUND")
    void curriculumNotFound() throws Exception {
        willThrow(new CurriculumNotFoundException())
                .given(studyStepService).start(anyString(), any(UUID.class));

        mockMvc.perform(post(startUrl(STEP_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CURRICULUM_NOT_FOUND"));
    }

    @Test
    @DisplayName("완료한 단계 재시작 → 409 STUDY_STEP_ALREADY_COMPLETED")
    void alreadyCompleted() throws Exception {
        willThrow(new StudyStepAlreadyCompletedException())
                .given(studyStepService).start(anyString(), any(UUID.class));

        mockMvc.perform(post(startUrl(STEP_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDY_STEP_ALREADY_COMPLETED"));
    }

    @Test
    @DisplayName("순서를 건너뛴 시작 → 409 INVALID_STUDY_STEP_ORDER")
    void invalidOrder() throws Exception {
        willThrow(new InvalidStudyStepOrderException())
                .given(studyStepService).start(anyString(), any(UUID.class));

        mockMvc.perform(post(startUrl(STEP_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STUDY_STEP_ORDER"));
    }

    @Test
    @DisplayName("다른 단계 진행 중 → 409 ANOTHER_STEP_IN_PROGRESS")
    void anotherStepInProgress() throws Exception {
        willThrow(new AnotherStepInProgressException())
                .given(studyStepService).start(anyString(), any(UUID.class));

        mockMvc.perform(post(startUrl(STEP_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ANOTHER_STEP_IN_PROGRESS"));
    }

    @Test
    @DisplayName("시험 시각 경과 → 409 EXAM_ALREADY_STARTED")
    void examAlreadyStarted() throws Exception {
        willThrow(new ExamAlreadyStartedException())
                .given(studyStepService).start(anyString(), any(UUID.class));

        mockMvc.perform(post(startUrl(STEP_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_ALREADY_STARTED"));
    }

    @Test
    @DisplayName("시작하지 않은 단계 완료 → 409 STUDY_STEP_NOT_STARTED")
    void notStarted() throws Exception {
        willThrow(new StudyStepNotStartedException())
                .given(studyStepService).complete(anyString(), any(UUID.class));

        mockMvc.perform(post(completeUrl(STEP_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDY_STEP_NOT_STARTED"));
    }

    @Test
    @DisplayName("없는 세션 → 404 SESSION_NOT_FOUND")
    void sessionNotFound() throws Exception {
        willThrow(new SessionNotFoundException())
                .given(studyStepService).start(anyString(), any(UUID.class));

        mockMvc.perform(post(startUrl(STEP_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("단계 id 형식이 잘못되면 → 400 INVALID_REQUEST")
    void invalidStepId() throws Exception {
        mockMvc.perform(post("/api/sessions/" + SESSION_CODE + "/steps/not-a-uuid/start"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
