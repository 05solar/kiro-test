package com.naeil.study.chat.dto;

import com.naeil.study.chat.entity.ChatMessage;
import java.util.List;

/**
 * {@code GET /api/sessions/{sessionCode}/chat} 응답.
 *
 * <p>세션의 대화 전체를 오래된 것부터 돌려준다. 다른 기기에서 8자리 코드로 들어와도
 * 나눈 대화가 그대로 이어져야 한다.
 *
 * <p>{@code grounded} 를 함께 준다. 화면이 대화창 머리에 "학습자료 기반 / 일반 지식 기반"을
 * 띄우기 위해 세션을 따로 조회하지 않아도 되게 한다.
 */
public record ChatHistoryResponse(boolean grounded, List<ChatMessageResponse> messages) {

    public static ChatHistoryResponse of(boolean grounded, List<ChatMessage> messages) {
        return new ChatHistoryResponse(grounded, messages.stream().map(ChatMessageResponse::from).toList());
    }
}
