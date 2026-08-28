package com.naeil.study.chat.dto;

import com.naeil.study.chat.entity.ChatMessage;
import com.naeil.study.chat.entity.ChatRole;
import java.time.LocalDateTime;

/**
 * 대화 한 줄.
 *
 * <p>내부 식별자(UUID)는 내보내지 않는다. 화면이 한 줄을 따로 지목할 일이 없다.
 */
public record ChatMessageResponse(
        ChatRole role,
        String content,
        LocalDateTime createdAt
) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(message.getRole(), message.getContent(), message.getCreatedAt());
    }
}
