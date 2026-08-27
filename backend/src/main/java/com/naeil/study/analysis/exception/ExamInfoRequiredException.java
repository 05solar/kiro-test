package com.naeil.study.analysis.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 시험 정보 없이 분석을 요청한 경우. → 400
 *
 * <p>과목명과 시험 일시가 없으면 AI가 무엇을 기준으로 우선순위를 판단할지 알 수 없다.
 */
public class ExamInfoRequiredException extends BusinessException {

    public ExamInfoRequiredException() {
        super(ErrorCode.EXAM_INFO_REQUIRED);
    }
}
