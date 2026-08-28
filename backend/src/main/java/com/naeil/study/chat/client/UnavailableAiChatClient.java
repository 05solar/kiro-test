package com.naeil.study.chat.client;

import com.naeil.study.chat.client.dto.AiChatAnswer;
import com.naeil.study.chat.client.dto.AiChatRequest;
import com.naeil.study.chat.exception.ChatFailedException;

/**
 * API 키가 설정되지 않았을 때 등록되는 클라이언트.
 *
 * <p>키가 없다고 애플리케이션이 뜨지 않으면 챗봇과 무관한 기능까지 막힌다.
 * 기동은 허용하고, 질문이 실제로 들어온 순간에만 실패시킨다.
 */
public class UnavailableAiChatClient implements AiChatClient {

    @Override
    public AiChatAnswer answer(AiChatRequest request) {
        throw new ChatFailedException("ai api key is not configured");
    }
}
