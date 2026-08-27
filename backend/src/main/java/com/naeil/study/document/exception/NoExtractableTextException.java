package com.naeil.study.document.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 파일은 정상적으로 읽혔지만 학습에 쓸 만한 텍스트가 없을 때 발생한다. → 422
 *
 * <p>스캔본 PDF처럼 이미지 레이어만 있는 문서가 대표적이다.
 * OCR은 MVP 범위가 아니므로 사용자에게 텍스트가 포함된 파일을 올리도록 안내한다.
 */
public class NoExtractableTextException extends BusinessException {

    public NoExtractableTextException() {
        super(ErrorCode.NO_EXTRACTABLE_TEXT);
    }
}
