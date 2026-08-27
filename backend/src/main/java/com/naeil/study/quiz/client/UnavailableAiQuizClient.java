package com.naeil.study.quiz.client;

import com.naeil.study.quiz.client.dto.AiQuizGenerationRequest;
import com.naeil.study.quiz.client.dto.AiQuizGenerationResult;
import com.naeil.study.quiz.exception.QuizGenerationFailedException;

/**
 * API 키가 설정되지 않았을 때 등록되는 클라이언트.
 *
 * <p>키가 없다고 애플리케이션이 뜨지 않으면 퀴즈와 무관한 기능까지 막힌다.
 * 기동은 허용하고, 퀴즈 생성을 실제로 요청한 순간에만 실패시킨다.
 */
public class UnavailableAiQuizClient implements AiQuizClient {

    @Override
    public AiQuizGenerationResult generate(AiQuizGenerationRequest request) {
        throw new QuizGenerationFailedException("ai api key is not configured");
    }
}
