package com.naeil.study.session.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 요청한 코드에 해당하는 세션이 없을 때 발생한다. → 404
 *
 * <p>코드 추측(brute force)에 힌트를 주지 않도록 존재하지 않는 세션과 만료된 세션을
 * 구분하지 않고 동일한 메시지를 사용한다.
 */
public class SessionNotFoundException extends BusinessException {

    public SessionNotFoundException() {
        super(ErrorCode.SESSION_NOT_FOUND);
    }
}
