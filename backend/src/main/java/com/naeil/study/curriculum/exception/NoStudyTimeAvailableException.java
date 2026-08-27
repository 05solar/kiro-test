package com.naeil.study.curriculum.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 남은 학습 시간이 0 이하인 경우. → 400
 *
 * <p>시험 시각이 이미 지났거나 학습 시간을 모두 소진한 상황이다.
 */
public class NoStudyTimeAvailableException extends BusinessException {

    public NoStudyTimeAvailableException() {
        super(ErrorCode.NO_STUDY_TIME_AVAILABLE);
    }
}
