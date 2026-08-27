package com.naeil.study.curriculum.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 분석된 주제가 하나도 없는 상태에서 계획을 요청한 경우. → 400
 *
 * <p>계획은 주제를 시간에 맞춰 배분하는 일이라 주제가 없으면 만들 것이 없다.
 */
public class TopicsRequiredException extends BusinessException {

    public TopicsRequiredException() {
        super(ErrorCode.TOPICS_REQUIRED);
    }
}
