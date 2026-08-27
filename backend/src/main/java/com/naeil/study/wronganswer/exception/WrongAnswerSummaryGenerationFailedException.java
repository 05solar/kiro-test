package com.naeil.study.wronganswer.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * AI 오답 요약 생성에 실패했을 때 발생한다. → 502
 *
 * <p>API 호출 실패, 응답 검증 실패가 모두 여기에 해당한다.
 * {@code reason}은 로그와 진단용 내부 요약이며 사용자 응답에는 나가지 않는다.
 *
 * <p>이 예외가 나가도 기존 요약은 지우지 않는다. 오래된 요약이라도 없는 것보다 낫다.
 */
public class WrongAnswerSummaryGenerationFailedException extends BusinessException {

    private final String reason;

    public WrongAnswerSummaryGenerationFailedException(String reason) {
        super(ErrorCode.WRONG_ANSWER_SUMMARY_GENERATION_FAILED);
        this.reason = reason;
    }

    public WrongAnswerSummaryGenerationFailedException(String reason, Throwable cause) {
        super(ErrorCode.WRONG_ANSWER_SUMMARY_GENERATION_FAILED);
        this.reason = reason;
        initCause(cause);
    }

    public String getReason() {
        return reason;
    }
}
