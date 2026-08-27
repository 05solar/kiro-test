package com.naeil.study.common.dto;

import com.naeil.study.common.exception.ErrorCode;

/**
 * 모든 에러 응답의 공통 형식.
 *
 * <pre>
 * {
 *   "code": "SESSION_NOT_FOUND",
 *   "message": "유효한 학습 세션을 찾을 수 없습니다."
 * }
 * </pre>
 */
public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getDefaultMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }
}
