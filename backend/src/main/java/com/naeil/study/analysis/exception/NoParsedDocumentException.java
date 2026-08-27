package com.naeil.study.analysis.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 텍스트 추출을 마친 강의자료가 하나도 없는 경우. → 400
 *
 * <p>업로드만 하고 파싱하지 않았거나, 모든 파일의 파싱이 실패한 상황이다.
 */
public class NoParsedDocumentException extends BusinessException {

    public NoParsedDocumentException() {
        super(ErrorCode.NO_PARSED_DOCUMENT);
    }
}
