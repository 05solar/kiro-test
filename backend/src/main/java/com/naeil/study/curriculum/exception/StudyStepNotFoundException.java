package com.naeil.study.curriculum.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 요청한 학습 단계가 없거나 다른 세션의 것인 경우. → 404
 *
 * <p>다른 세션 소유일 때 403을 주면 "그 단계는 존재한다"는 사실이 드러난다.
 * 세션 코드가 유일한 접근 키이므로 존재 여부 자체를 알려주지 않는다.
 */
public class StudyStepNotFoundException extends BusinessException {

    public StudyStepNotFoundException() {
        super(ErrorCode.STUDY_STEP_NOT_FOUND);
    }
}
