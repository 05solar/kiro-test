package com.naeil.study.curriculum.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 남은 시간으로 유효한 학습 계획을 만들 수 없을 때 발생한다. → 422
 *
 * <p>남은 시간이 주제 하나의 최소 학습시간에도 못 미치는 경우가 대표적이다.
 *
 * <p>{@code reason}은 로그와 진단용 내부 요약이며 사용자 응답에는 나가지 않는다.
 * 계획이 지켜야 할 조건이 깨졌을 때도 이 예외를 쓴다. 조용히 잘못된 계획을 내보내지 않는다.
 */
public class CurriculumGenerationFailedException extends BusinessException {

    private final String reason;

    public CurriculumGenerationFailedException(String reason) {
        super(ErrorCode.CURRICULUM_GENERATION_FAILED);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
