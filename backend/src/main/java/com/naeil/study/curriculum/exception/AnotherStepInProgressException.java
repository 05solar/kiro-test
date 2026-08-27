package com.naeil.study.curriculum.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 이미 다른 단계가 진행 중인데 새 단계를 시작하려는 경우. → 409
 *
 * <p>실제 학습시간은 시작~완료 사이 시간으로 계산한다. 두 단계가 동시에 진행 중이면
 * 같은 시간이 양쪽에 기록되어 이후 재조정의 근거가 무너진다.
 */
public class AnotherStepInProgressException extends BusinessException {

    public AnotherStepInProgressException() {
        super(ErrorCode.ANOTHER_STEP_IN_PROGRESS);
    }
}
