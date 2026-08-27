package com.naeil.study.curriculum.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 시험 시각이 지난 뒤에 새 단계를 시작하려는 경우. → 409
 *
 * <p>400이 아니라 409인 이유는 요청 값이 잘못된 것이 아니라
 * 세션의 현재 상태와 현실 시간이 충돌하기 때문이다.
 *
 * <p>이미 진행 중인 단계의 <b>완료</b>는 막지 않는다. 실제 학습 기록은 남겨야 한다.
 */
public class ExamAlreadyStartedException extends BusinessException {

    public ExamAlreadyStartedException() {
        super(ErrorCode.EXAM_ALREADY_STARTED);
    }
}
