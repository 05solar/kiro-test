package com.naeil.study.session.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 시험 일시가 현재 시각보다 과거일 때 발생한다. → 400
 *
 * <p>Bean Validation의 {@code @Future} 대신 서비스에서 검증한다.
 * {@code @Future}는 시스템 시계를 쓰기 때문에 주입한 {@code Clock}으로 시간을 고정한 테스트와
 * 결과가 어긋난다. 시간 판단은 프로젝트 전체에서 {@code Clock} 하나로 통일한다.
 */
public class InvalidExamTimeException extends BusinessException {

    public InvalidExamTimeException() {
        super(ErrorCode.INVALID_EXAM_TIME);
    }
}
