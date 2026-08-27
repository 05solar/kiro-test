package com.naeil.study.topic.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import com.naeil.study.topic.service.TopicService;
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

@WebMvcTest(TopicController.class)
@DisplayName("TopicController - Topic 조회 API")
class TopicControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 18, 0, 0);
    private static final String SESSION_CODE = "7K2M9QXF";
    private static final UUID TOPIC_ID = UUID.fromString("22222222-0000-4000-8000-000000000001");
    private static final UUID DOC_ID = UUID.fromString("11111111-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopicService topicService;

    private Topic topic(String title, TopicImportance importance, int order) throws Exception {
        StudySession session = StudySession.create(SESSION_CODE, NOW, 30L);
        Topic topic = Topic.create(session, title, "요약 내용",
                List.of("FCFS", "SJF", "Round Robin"), importance, 35,
                false, true, false, false, List.of(DOC_ID), order, NOW);
        Field field = Topic.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(topic, TOPIC_ID);
        return topic;
    }

    @Test
    @DisplayName("GET /topics → 200, 분석된 Topic 목록")
    void findAllReturns200() throws Exception {
        given(topicService.findAll(SESSION_CODE))
                .willReturn(List.of(topic("CPU 스케줄링", TopicImportance.VERY_HIGH, 2)));

        mockMvc.perform(get("/api/sessions/{sessionCode}/topics", SESSION_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics[0].id").value(TOPIC_ID.toString()))
                .andExpect(jsonPath("$.topics[0].title").value("CPU 스케줄링"))
                .andExpect(jsonPath("$.topics[0].summary").value("요약 내용"))
                .andExpect(jsonPath("$.topics[0].keyPoints[0]").value("FCFS"))
                .andExpect(jsonPath("$.topics[0].keyPoints.length()").value(3))
                .andExpect(jsonPath("$.topics[0].importance").value("VERY_HIGH"))
                .andExpect(jsonPath("$.topics[0].estimatedStudyMinutes").value(35))
                .andExpect(jsonPath("$.topics[0].professorEmphasisMatched").value(false))
                .andExpect(jsonPath("$.topics[0].pastExamMatched").value(true))
                .andExpect(jsonPath("$.topics[0].weakAreaMatched").value(false))
                .andExpect(jsonPath("$.topics[0].mustStudyMatched").value(false))
                .andExpect(jsonPath("$.topics[0].topicOrder").value(2))
                // 출처 문서 ID는 응답에 넣지 않는다.
                .andExpect(jsonPath("$.topics[0].sourceDocumentIds").doesNotExist());
    }

    @Test
    @DisplayName("GET /topics 아직 분석하지 않았으면 → 200, 빈 배열")
    void findAllReturnsEmptyArray() throws Exception {
        given(topicService.findAll(SESSION_CODE)).willReturn(List.of());

        mockMvc.perform(get("/api/sessions/{sessionCode}/topics", SESSION_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics").isArray())
                .andExpect(jsonPath("$.topics.length()").value(0));
    }

    @Test
    @DisplayName("GET /topics 존재하지 않는 세션 → 404")
    void findAllReturns404() throws Exception {
        willThrow(new SessionNotFoundException()).given(topicService).findAll(anyString());

        mockMvc.perform(get("/api/sessions/{sessionCode}/topics", "ZZZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }
}
