package com.naeil.study.document.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 세션에 보관할 수 있는 파일 개수를 넘은 경우. → 400
 *
 * <p>이미 저장된 개수와 이번 요청의 개수를 합쳐서 판단한다.
 */
public class FileCountExceededException extends BusinessException {

    public FileCountExceededException() {
        super(ErrorCode.FILE_COUNT_EXCEEDED);
    }
}
