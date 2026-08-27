package com.naeil.study.quiz.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * AI 퀴즈 생성에 실패했을 때 발생한다. → 502
 *
 * <p>API 호출 실패, 응답 검증 실패(보기 수, 정답 인덱스, 난이도 등)가 모두 여기에 해당한다.
 * {@code reason}은 로그와 진단용 내부 요약이며 사용자 응답에는 나가지 않는다.
 *
 * <p>이 예외가 나가도 아무것도 저장되지 않는다. 사용자는 다시 생성을 요청할 수 있다.
 */
public class QuizGenerationFailedException extends BusinessException {

    private final String reason;

    public QuizGenerationFailedException(String reason) {
        super(ErrorCode.QUIZ_GENERATION_FAILED);
        this.reason = reason;
    }

    public QuizGenerationFailedException(String reason, Throwable cause) {
        super(ErrorCode.QUIZ_GENERATION_FAILED);
        this.reason = reason;
        initCause(cause);
    }

    public String getReason() {
        return reason;
    }
}
