package com.naeil.study.curriculum.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * AI 분석이 끝나지 않은 세션에서 계획을 요청한 경우. → 400
 *
 * <p>분석 중이거나 실패한 상태에서 계획을 만들면 곧 교체될 주제로 계획을 세우게 된다.
 */
public class SessionNotReadyException extends BusinessException {

    public SessionNotReadyException() {
        super(ErrorCode.SESSION_NOT_READY);
    }
}
