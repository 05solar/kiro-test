package com.naeil.study.topic.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 없거나 다른 세션의 Topic 인 경우. → 404
 *
 * <p>다른 세션 소유일 때 403이 아니라 404를 주는 이유는, 403이면
 * "그 Topic 은 존재한다"는 사실이 드러나기 때문이다.
 */
public class TopicNotFoundException extends BusinessException {

    public TopicNotFoundException() {
        super(ErrorCode.TOPIC_NOT_FOUND);
    }
}
