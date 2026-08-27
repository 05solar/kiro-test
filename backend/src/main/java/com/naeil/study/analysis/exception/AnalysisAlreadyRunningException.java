package com.naeil.study.analysis.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 이미 분석 중인 세션에 다시 분석을 요청한 경우. → 409
 *
 * <p>같은 자료를 두 번 분석해 결과를 덮어쓰는 것과, AI 호출 비용이 두 배로 드는 것을 막는다.
 */
public class AnalysisAlreadyRunningException extends BusinessException {

    public AnalysisAlreadyRunningException() {
        super(ErrorCode.ANALYSIS_ALREADY_RUNNING);
    }
}
