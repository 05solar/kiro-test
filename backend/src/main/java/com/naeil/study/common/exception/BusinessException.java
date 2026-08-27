package com.naeil.study.common.exception;

/**
 * 서비스 규칙 위반을 나타내는 예외의 공통 상위 타입.
 *
 * <p>{@link ErrorCode}를 들고 있으므로 {@link GlobalExceptionHandler}가
 * 별도 분기 없이 HTTP 상태와 응답 본문을 만들 수 있다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
