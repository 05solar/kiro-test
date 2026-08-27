package com.naeil.study.document.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 이미 파싱 중인 문서에 다시 파싱을 요청한 경우. → 409
 *
 * <p>같은 문서를 두 번 읽어 결과를 덮어쓰는 것을 막는다.
 */
public class DocumentAlreadyParsingException extends BusinessException {

    public DocumentAlreadyParsingException() {
        super(ErrorCode.DOCUMENT_ALREADY_PARSING);
    }
}
