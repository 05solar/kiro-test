package com.naeil.study.chat.controller;

import com.naeil.study.chat.dto.ChatHistoryResponse;
import com.naeil.study.chat.dto.ChatRequest;
import com.naeil.study.chat.dto.ChatResponse;
import com.naeil.study.chat.service.ChatService;
import com.naeil.study.chat.service.ChatService.ChatHistory;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학습 챗봇 API.
 *
 * <p><b>경로를 세션 아래에 둔다.</b> 이 서비스에는 회원이 없고 8자리 코드가 유일한 접근
 * 키다. 세션 밖의 경로(예: {@code /api/chat})에서는 이 대화가 누구 것인지 확인할 방법이
 * 없다. 다른 모든 엔드포인트와 같은 소유권 검사를 지나게 하기 위해 세션 아래에 둔다.
 *
 * <p>지난 대화는 요청 본문에 넣지 않는다. 서버가 갖고 있다. 클라이언트가 보내오게 두면
 * 없던 발화를 지어내 프롬프트에 넣을 수 있다.
 */
@RestController
@RequestMapping("/api/sessions/{sessionCode}/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 질문하고 답을 받는다. 실제 AI 를 부른다.
     *
     * <p>{@code chat.ai-mode=mock} 이면 AI 를 부르지 않고 목 응답을 돌려준다(기본값).
     */
    @PostMapping
    public ResponseEntity<ChatResponse> ask(
            @PathVariable String sessionCode,
            @Valid @RequestBody ChatRequest request
    ) {
        return ResponseEntity.ok(chatService.ask(sessionCode, request.message()));
    }

    /**
     * 이 세션에서 나눈 대화 전체를 조회한다. 오래된 것부터.
     *
     * <p>AI 를 부르지 않는다. 화면을 다시 열거나 다른 기기에서 들어왔을 때 쓴다.
     */
    @GetMapping
    public ResponseEntity<ChatHistoryResponse> history(@PathVariable String sessionCode) {
        ChatHistory history = chatService.history(sessionCode);
        return ResponseEntity.ok(ChatHistoryResponse.of(history.grounded(), history.messages()));
    }
}
