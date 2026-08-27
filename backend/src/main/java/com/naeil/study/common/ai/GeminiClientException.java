package com.naeil.study.common.ai;

/**
 * Gemini 호출 계층의 실패.
 *
 * <p>도메인까지 그대로 올라가지 않는다. 각 도메인의 Gemini 구현체가 받아서
 * 자기 도메인 예외(502 계열)로 감싼다.
 */
public class GeminiClientException extends RuntimeException {

    public GeminiClientException(String message) {
        super(message);
    }

    public GeminiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
