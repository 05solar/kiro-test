package com.naeil.study.session.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 세션 코드 형식이 규칙에 맞지 않을 때 발생한다. → 400
 *
 * <p>형식 검사 단계에서 걸러내므로 DB 조회를 수행하지 않는다.
 */
public class InvalidSessionCodeException extends BusinessException {

    public InvalidSessionCodeException() {
        super(ErrorCode.INVALID_SESSION_CODE);
    }
}
