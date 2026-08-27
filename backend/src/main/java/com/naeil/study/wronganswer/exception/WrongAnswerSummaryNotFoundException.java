package com.naeil.study.wronganswer.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 아직 오답 복습 요약을 생성하지 않은 세션에서 조회한 경우. → 404
 */
public class WrongAnswerSummaryNotFoundException extends BusinessException {

    public WrongAnswerSummaryNotFoundException() {
        super(ErrorCode.WRONG_ANSWER_SUMMARY_NOT_FOUND);
    }
}
