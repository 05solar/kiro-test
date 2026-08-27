package com.naeil.study.quiz.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 답안의 보기 번호가 0~3 범위를 벗어난 경우. → 400
 */
public class InvalidQuizOptionException extends BusinessException {

    public InvalidQuizOptionException() {
        super(ErrorCode.INVALID_QUIZ_OPTION);
    }
}
