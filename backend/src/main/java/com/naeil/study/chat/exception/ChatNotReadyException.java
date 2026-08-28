package com.naeil.study.chat.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 아직 답할 근거가 없는 세션에 질문했을 때 발생한다. → 409
 *
 * <p>분석 전에는 학습 주제도 자료 추출 결과도 없다. 그 상태에서 답하면 무엇에도 근거하지
 * 않은 일반 상식이 나가고, 사용자는 그것을 자기 시험 범위의 내용으로 받아들인다.
 * 실패로 알리고 먼저 분석을 마치게 한다.
 */
public class ChatNotReadyException extends BusinessException {

    public ChatNotReadyException() {
        super(ErrorCode.CHAT_NOT_READY);
    }
}
