package com.naeil.study.chat.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 챗봇이 답하지 못했을 때 발생한다. → 502
 *
 * <p>API 호출 실패와 응답 형식 오류가 모두 여기에 해당한다. {@code reason} 은 로그와
 * 진단용 내부 요약이며 사용자 응답에는 나가지 않는다. 라이브러리 예외 메시지가 화면에
 * 그대로 나가면 내부 구조가 드러난다.
 *
 * <p>이 예외가 나가면 <b>질문도 저장하지 않는다.</b> 답이 없는 질문만 대화 기록에 남으면
 * 다음 요청의 프롬프트에 "답하지 않은 질문"이 섞여 들어간다.
 */
public class ChatFailedException extends BusinessException {

    private final String reason;

    public ChatFailedException(String reason) {
        super(ErrorCode.CHAT_FAILED);
        this.reason = reason;
    }

    public ChatFailedException(String reason, Throwable cause) {
        super(ErrorCode.CHAT_FAILED);
        this.reason = reason;
        initCause(cause);
    }

    public String getReason() {
        return reason;
    }
}
