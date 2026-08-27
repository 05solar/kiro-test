package com.naeil.study.session.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 정해진 횟수 안에 중복되지 않는 세션 코드를 만들지 못했을 때 발생한다. → 500
 *
 * <p>정상 상황에서는 발생하지 않는다. 발생한다면 코드 공간이 포화되었거나
 * 중복 검사 경로에 문제가 있다는 신호다.
 */
public class SessionCodeGenerationException extends BusinessException {

    public SessionCodeGenerationException(int attempts) {
        super(ErrorCode.SESSION_CODE_GENERATION_FAILED,
                ErrorCode.SESSION_CODE_GENERATION_FAILED.getDefaultMessage());
        this.attempts = attempts;
    }

    private final int attempts;

    public int getAttempts() {
        return attempts;
    }
}
