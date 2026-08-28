package com.naeil.study.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naeil.study.chat.dto.ChatResponse;
import com.naeil.study.chat.entity.ChatMessage;
import com.naeil.study.chat.exception.ChatFailedException;
import com.naeil.study.chat.exception.ChatNotReadyException;
import com.naeil.study.chat.service.ChatService;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.exception.SessionNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatController.class)
@DisplayName("ChatController - 학습 챗봇 API")
class ChatControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 22, 0, 0);
    private static final String SESSION_CODE = "7K2M9QXF";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    private String body(String message) {
        return """
                {"message": %s}
                """.formatted(message == null ? "null" : "\"" + message + "\"");
    }

    @Nested
    @DisplayName("POST /api/sessions/{sessionCode}/chat")
    class Ask {

        @Test
        @DisplayName("정상 질문 → 200, 답변과 근거 표시를 반환한다")
        void returns200() throws Exception {
            given(chatService.ask(eq(SESSION_CODE), any(String.class)))
                    .willReturn(new ChatResponse("프로세스는 실행 중인 프로그램입니다.", true, true, NOW));

            mockMvc.perform(post("/api/sessions/{sessionCode}/chat", SESSION_CODE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("프로세스가 뭐야?")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.answer").value("프로세스는 실행 중인 프로그램입니다."))
                    .andExpect(jsonPath("$.grounded").value(true))
                    .andExpect(jsonPath("$.answeredFromMaterial").value(true))
                    .andExpect(jsonPath("$.answeredAt").value("2026-08-27T22:00:00"));
        }

        @Test
        @DisplayName("빈 질문 → 400, 서비스를 부르지 않는다")
        void rejectsBlankMessage() throws Exception {
            mockMvc.perform(post("/api/sessions/{sessionCode}/chat", SESSION_CODE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("   ")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verifyNoInteractions(chatService);
        }

        @Test
        @DisplayName("1000자를 넘는 질문 → 400. 자료 한 편을 붙여 넣는 용도가 아니다")
        void rejectsTooLongMessage() throws Exception {
            mockMvc.perform(post("/api/sessions/{sessionCode}/chat", SESSION_CODE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("가".repeat(1001))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verifyNoInteractions(chatService);
        }

        @Test
        @DisplayName("없는 세션 → 404. 만료된 세션과 같은 응답이다")
        void returns404ForUnknownSession() throws Exception {
            willThrow(new SessionNotFoundException())
                    .given(chatService).ask(eq(SESSION_CODE), any(String.class));

            mockMvc.perform(post("/api/sessions/{sessionCode}/chat", SESSION_CODE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("질문")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
        }

        @Test
        @DisplayName("분석 전 세션 → 409 CHAT_NOT_READY")
        void returns409BeforeAnalysis() throws Exception {
            willThrow(new ChatNotReadyException())
                    .given(chatService).ask(eq(SESSION_CODE), any(String.class));

            mockMvc.perform(post("/api/sessions/{sessionCode}/chat", SESSION_CODE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("질문")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CHAT_NOT_READY"));
        }

        @Test
        @DisplayName("AI 실패 → 502. 내부 사유는 응답에 나가지 않는다")
        void returns502OnAiFailure() throws Exception {
            willThrow(new ChatFailedException("gemini returned unparsable json"))
                    .given(chatService).ask(eq(SESSION_CODE), any(String.class));

            mockMvc.perform(post("/api/sessions/{sessionCode}/chat", SESSION_CODE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("질문")))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("CHAT_FAILED"))
                    .andExpect(jsonPath("$.message").value("답변을 만들지 못했습니다. 다시 시도해 주세요."));
        }
    }

    @Nested
    @DisplayName("GET /api/sessions/{sessionCode}/chat")
    class ReadHistory {

        @Test
        @DisplayName("대화를 오래된 것부터 반환한다")
        void returnsHistory() throws Exception {
            StudySession session = StudySession.create(SESSION_CODE, NOW.minusHours(1), 30L);
            given(chatService.history(SESSION_CODE)).willReturn(new ChatService.ChatHistory(
                    true,
                    List.of(
                            ChatMessage.user(session, "프로세스가 뭐야?", 1, NOW.minusMinutes(2)),
                            ChatMessage.assistant(session, "실행 중인 프로그램입니다.", 2, NOW.minusMinutes(2)))));

            mockMvc.perform(get("/api/sessions/{sessionCode}/chat", SESSION_CODE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.grounded").value(true))
                    .andExpect(jsonPath("$.messages.length()").value(2))
                    .andExpect(jsonPath("$.messages[0].role").value("USER"))
                    .andExpect(jsonPath("$.messages[0].content").value("프로세스가 뭐야?"))
                    .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"))
                    // 내부 식별자는 내보내지 않는다.
                    .andExpect(jsonPath("$.messages[0].id").doesNotExist());
        }

        @Test
        @DisplayName("대화가 없어도 200 과 빈 목록을 반환한다")
        void returnsEmptyHistory() throws Exception {
            given(chatService.history(SESSION_CODE))
                    .willReturn(new ChatService.ChatHistory(false, List.of()));

            mockMvc.perform(get("/api/sessions/{sessionCode}/chat", SESSION_CODE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.grounded").value(false))
                    .andExpect(jsonPath("$.messages.length()").value(0));
        }
    }
}
