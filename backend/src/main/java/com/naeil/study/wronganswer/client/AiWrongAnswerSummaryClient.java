package com.naeil.study.wronganswer.client;

import com.naeil.study.wronganswer.client.dto.AiWrongAnswerSummaryRequest;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerSummaryResult;

/**
 * AI 오답 요약 호출 추상화.
 *
 * <p>도메인 서비스는 이 인터페이스만 안다. 어느 LLM 공급자를 쓰는지 모른다.
 * 현재 구현은 프로젝트 공통 AI 스택(Anthropic)을 재사용하며, 다른 공급자로 바꾸려면
 * 이 인터페이스의 구현체와 설정만 교체하면 된다.
 *
 * <p>구현체는 실패를
 * {@link com.naeil.study.wronganswer.exception.WrongAnswerSummaryGenerationFailedException}
 * 으로 감싼다. SDK 예외를 그대로 올리지 않는다.
 */
public interface AiWrongAnswerSummaryClient {

    /** 세션의 오답들로 Topic 별 복습 요약을 생성한다. */
    AiWrongAnswerSummaryResult generate(AiWrongAnswerSummaryRequest request);
}
