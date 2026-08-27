package com.naeil.study.quiz.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 퀴즈의 근거로 쓸 강의자료 텍스트를 찾을 수 없는 경우. → 400
 *
 * <p>Topic 의 출처 문서가 지워졌거나 추출된 텍스트가 없는 경우다.
 * 근거 없이 일반 지식으로 문제를 만들어 내지 않는다.
 */
public class NoQuizSourceContextException extends BusinessException {

    public NoQuizSourceContextException() {
        super(ErrorCode.NO_QUIZ_SOURCE_CONTEXT);
    }
}
