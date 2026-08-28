package com.naeil.study.chat.client.dto;

/**
 * 지난 대화 한 줄.
 *
 * @param assistant 챗봇이 한 말이면 true, 사용자가 한 말이면 false
 * @param content   발화 내용
 */
public record AiChatTurn(boolean assistant, String content) {
}
