package com.naeil.study.document.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/** 내용이 없는 파일을 업로드한 경우. → 400 */
public class EmptyFileException extends BusinessException {

    public EmptyFileException() {
        super(ErrorCode.EMPTY_FILE);
    }
}
