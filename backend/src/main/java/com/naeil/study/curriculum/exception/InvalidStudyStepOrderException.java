package com.naeil.study.curriculum.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 앞선 단계를 건너뛰고 뒤 단계를 시작하려는 경우. → 409
 *
 * <p>계획은 중요도 순으로 배치되어 있다. 뒤 단계부터 하면 시간이 모자랄 때
 * 정작 중요한 단계가 남는다.
 */
public class InvalidStudyStepOrderException extends BusinessException {

    public InvalidStudyStepOrderException() {
        super(ErrorCode.INVALID_STUDY_STEP_ORDER);
    }
}
