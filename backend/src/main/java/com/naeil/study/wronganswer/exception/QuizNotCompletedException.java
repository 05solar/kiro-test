package com.naeil.study.wronganswer.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 세션의 퀴즈를 아직 다 풀지 않았는데 오답 요약을 요청한 경우. → 409
 *
 * <p>절반만 푼 상태의 요약은 남은 문제를 푸는 순간 낡은 자료가 된다.
 * 요약은 대상 범위의 채점이 모두 끝난 뒤에 만든다.
 */
public class QuizNotCompletedException extends BusinessException {

    public QuizNotCompletedException() {
        super(ErrorCode.QUIZ_NOT_COMPLETED);
    }
}
