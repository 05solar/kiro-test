package com.naeil.study.document.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/** 개별 파일이 허용 크기를 넘은 경우. → 400 */
public class FileSizeExceededException extends BusinessException {

    public FileSizeExceededException() {
        super(ErrorCode.FILE_SIZE_EXCEEDED);
    }
}
