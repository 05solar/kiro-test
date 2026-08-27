package com.naeil.study.curriculum.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/** 이미 완료한 단계를 다시 시작하려는 경우. → 409 */
public class StudyStepAlreadyCompletedException extends BusinessException {

    public StudyStepAlreadyCompletedException() {
        super(ErrorCode.STUDY_STEP_ALREADY_COMPLETED);
    }
}
