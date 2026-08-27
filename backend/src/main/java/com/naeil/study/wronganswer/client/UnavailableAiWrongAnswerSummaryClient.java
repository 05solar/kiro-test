package com.naeil.study.wronganswer.client;

import com.naeil.study.wronganswer.client.dto.AiWrongAnswerSummaryRequest;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerSummaryResult;
import com.naeil.study.wronganswer.exception.WrongAnswerSummaryGenerationFailedException;

/**
 * API 키가 설정되지 않았을 때 등록되는 클라이언트.
 *
 * <p>기동은 허용하고, 오답 요약을 실제로 요청한 순간에만 실패시킨다.
 */
public class UnavailableAiWrongAnswerSummaryClient implements AiWrongAnswerSummaryClient {

    @Override
    public AiWrongAnswerSummaryResult generate(AiWrongAnswerSummaryRequest request) {
        throw new WrongAnswerSummaryGenerationFailedException("ai api key is not configured");
    }
}
