package com.naeil.study.analysis.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * AI 분석에 실패했을 때 발생한다. → 502
 *
 * <p>API 호출 실패, 응답 검증 실패, 결과 없음 등이 모두 여기에 해당한다.
 * {@code reason}은 로그와 진단용 내부 요약이며 사용자 응답에는 나가지 않는다.
 *
 * <p>이 예외가 나가도 세션과 강의자료, 학습 맥락은 지우지 않는다.
 * 세션 상태만 {@code ANALYSIS_FAILED}가 되고 사용자는 다시 분석을 요청할 수 있다.
 */
public class AiAnalysisException extends BusinessException {

    private final String reason;

    public AiAnalysisException(String reason) {
        super(ErrorCode.ANALYSIS_FAILED);
        this.reason = reason;
    }

    public AiAnalysisException(String reason, Throwable cause) {
        super(ErrorCode.ANALYSIS_FAILED);
        this.reason = reason;
        initCause(cause);
    }

    public String getReason() {
        return reason;
    }
}
