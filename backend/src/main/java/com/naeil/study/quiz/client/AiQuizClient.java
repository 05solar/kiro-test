package com.naeil.study.quiz.client;

import com.naeil.study.quiz.client.dto.AiQuizGenerationRequest;
import com.naeil.study.quiz.client.dto.AiQuizGenerationResult;

/**
 * AI 퀴즈 생성 호출 추상화.
 *
 * <p>분석({@code AiAnalysisClient})과 역할을 분리한다. 한 인터페이스에 기능을 몰아넣으면
 * 프롬프트·응답 형식·재시도 정책이 서로 얽힌다. 공급자 SDK 설정은 재사용하되 계약은 나눈다.
 *
 * <p>구현체는 실패를 {@link com.naeil.study.quiz.exception.QuizGenerationFailedException}으로
 * 감싼다. SDK 예외를 그대로 올리지 않는다.
 */
public interface AiQuizClient {

    /** Topic 하나의 4지선다 문제들을 생성한다. */
    AiQuizGenerationResult generate(AiQuizGenerationRequest request);
}
