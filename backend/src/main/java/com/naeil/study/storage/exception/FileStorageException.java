package com.naeil.study.storage.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 파일 저장소 작업에 실패했을 때 발생한다. → 500
 *
 * <p>원인 예외는 로그에만 남긴다. 서버 경로나 OS 오류 메시지를 응답에 노출하지 않는다.
 */
public class FileStorageException extends BusinessException {

    public FileStorageException(Throwable cause) {
        super(ErrorCode.FILE_STORAGE_FAILED);
        initCause(cause);
    }

    public FileStorageException() {
        super(ErrorCode.FILE_STORAGE_FAILED);
    }
}
