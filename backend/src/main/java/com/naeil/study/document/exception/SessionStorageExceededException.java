package com.naeil.study.document.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 세션 전체 파일 용량 한도를 넘은 경우. → 400
 *
 * <p>이미 저장된 용량과 이번 요청의 용량을 합쳐서 판단한다.
 */
public class SessionStorageExceededException extends BusinessException {

    public SessionStorageExceededException() {
        super(ErrorCode.SESSION_STORAGE_EXCEEDED);
    }
}
