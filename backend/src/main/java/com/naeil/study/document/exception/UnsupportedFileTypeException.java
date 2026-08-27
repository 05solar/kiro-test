package com.naeil.study.document.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/** PDF, DOCX, TXT가 아닌 파일을 업로드한 경우. → 400 */
public class UnsupportedFileTypeException extends BusinessException {

    public UnsupportedFileTypeException() {
        super(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }
}
