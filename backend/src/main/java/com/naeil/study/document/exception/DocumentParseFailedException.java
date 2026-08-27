package com.naeil.study.document.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 문서에서 텍스트를 추출하지 못했을 때 발생한다. → 422
 *
 * <p>파일 손상, 암호 걸린 PDF, 디코딩 실패 등이 여기에 해당한다.
 *
 * <p>{@code reason}은 DB의 {@code parse_error_message} 와 로그에만 쓰는 내부 요약이다.
 * 사용자 응답에는 {@link ErrorCode#DOCUMENT_PARSE_FAILED} 의 안내 문구가 나간다.
 * 라이브러리 원문 메시지나 스택트레이스를 담지 않는다.
 */
public class DocumentParseFailedException extends BusinessException {

    private final String reason;

    public DocumentParseFailedException(String reason) {
        super(ErrorCode.DOCUMENT_PARSE_FAILED);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
