package com.naeil.study.quiz.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 없거나 다른 세션의 퀴즈인 경우, 또는 아직 퀴즈를 생성하지 않은 경우. → 404
 */
public class QuizNotFoundException extends BusinessException {

    public QuizNotFoundException() {
        super(ErrorCode.QUIZ_NOT_FOUND);
    }
}
