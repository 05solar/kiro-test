package com.naeil.study.quiz.exception;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.common.exception.ErrorCode;

/**
 * 해당 Topic 의 학습 단계를 아직 완료하지 않았는데 퀴즈를 요청한 경우. → 409
 *
 * <p>퀴즈는 학습을 마친 뒤의 점검 도구다. 계획에서 제외된(SKIPPED) Topic 도
 * 학습을 완료하지 않은 것이므로 같은 코드로 다룬다.
 */
public class TopicStudyNotCompletedException extends BusinessException {

    public TopicStudyNotCompletedException() {
        super(ErrorCode.TOPIC_STUDY_NOT_COMPLETED);
    }
}
