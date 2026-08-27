package com.naeil.study.document.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 요청한 강의자료가 해당 세션에 없을 때 발생한다. → 404
 *
 * <p>다른 세션 소유의 문서를 요청한 경우에도 이 예외를 쓴다.
 * 403으로 응답하면 "그 ID의 문서가 어딘가에 존재한다"는 사실이 새어 나간다.
 */
public class DocumentNotFoundException extends BusinessException {

    public DocumentNotFoundException() {
        super(ErrorCode.DOCUMENT_NOT_FOUND);
    }
}
