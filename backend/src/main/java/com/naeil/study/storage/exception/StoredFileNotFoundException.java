package com.naeil.study.storage.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * DB에는 메타데이터가 있는데 Storage에 실제 파일이 없을 때 발생한다.
 *
 * <p>저장소 장애({@link FileStorageException}, 500)와 구분한다.
 * 이 상황은 사용자가 파일을 다시 올리면 해결되므로 422로 응답한다.
 */
public class StoredFileNotFoundException extends BusinessException {

    public StoredFileNotFoundException() {
        super(ErrorCode.STORED_FILE_NOT_FOUND);
    }
}
