package com.naeil.study.curriculum.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/** 아직 학습 계획을 만들지 않은 세션을 조회한 경우. → 404 */
public class CurriculumNotFoundException extends BusinessException {

    public CurriculumNotFoundException() {
        super(ErrorCode.CURRICULUM_NOT_FOUND);
    }
}
