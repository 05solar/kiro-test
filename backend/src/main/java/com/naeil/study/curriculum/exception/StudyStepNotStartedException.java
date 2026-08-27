package com.naeil.study.curriculum.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 시작하지 않은 단계를 완료하려는 경우. → 409
 *
 * <p>시작 기록이 없으면 실제 학습시간을 계산할 근거가 없다.
 */
public class StudyStepNotStartedException extends BusinessException {

    public StudyStepNotStartedException() {
        super(ErrorCode.STUDY_STEP_NOT_STARTED);
    }
}
