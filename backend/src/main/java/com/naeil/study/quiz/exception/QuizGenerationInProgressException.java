package com.naeil.study.quiz.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 같은 Topic 의 새 퀴즈 생성이 이미 진행 중인 경우. → 409
 *
 * <p>버튼을 두 번 누르거나 응답을 기다리다 새로고침하면 AI 가 두 번 불린다.
 * 그만큼 과금되고 회차도 둘로 갈린다.
 */
public class QuizGenerationInProgressException extends BusinessException {

    public QuizGenerationInProgressException() {
        super(ErrorCode.QUIZ_GENERATION_IN_PROGRESS);
    }
}
