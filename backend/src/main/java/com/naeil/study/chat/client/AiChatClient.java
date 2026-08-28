package com.naeil.study.chat.client;

import com.naeil.study.chat.client.dto.AiChatAnswer;
import com.naeil.study.chat.client.dto.AiChatRequest;

/**
 * 학습 챗봇 호출 추상화.
 *
 * <p>분석({@code AiAnalysisClient})·퀴즈({@code AiQuizClient})와 계약을 나눈다. 세 기능은
 * 프롬프트도 응답 형식도 실패 처리도 다르다. 공급자 SDK 설정만 재사용한다.
 *
 * <p>구현체는 실패를 {@link com.naeil.study.chat.exception.ChatFailedException} 으로 감싼다.
 * SDK 예외를 그대로 올리면 내부 라이브러리 메시지가 사용자 화면까지 나간다.
 */
public interface AiChatClient {

    /** 학습 맥락과 지난 대화를 바탕으로 질문에 답한다. */
    AiChatAnswer answer(AiChatRequest request);
}
