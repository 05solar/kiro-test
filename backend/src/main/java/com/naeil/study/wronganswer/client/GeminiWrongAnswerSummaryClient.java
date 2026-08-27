package com.naeil.study.wronganswer.client;

import com.naeil.study.common.ai.GeminiTextClient;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerSummaryRequest;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerSummaryResult;
import com.naeil.study.wronganswer.exception.WrongAnswerSummaryGenerationFailedException;
import com.naeil.study.wronganswer.prompt.WrongAnswerSummaryPrompts;

/**
 * Gemini 기반 오답 요약 클라이언트.
 *
 * <p>기획 문서가 전제한 Gemini 공급자 구현이다. 프롬프트는 공급자와 무관하게
 * {@link WrongAnswerSummaryPrompts} 하나를 쓰고, 응답 값 검증은 기존
 * {@code AiWrongAnswerSummaryValidator} 가 담당한다.
 */
public class GeminiWrongAnswerSummaryClient implements AiWrongAnswerSummaryClient {

    private static final String OUTPUT_FORMAT = """

            # OUTPUT FORMAT (JSON only)
            {"overallReview": "전체 총평 한두 문장",
             "topics": [{
               "topicReference": "TOPIC_1",
               "wrongConcepts": ["틀린 개념 (최소 1개)"],
               "summary": "핵심 복습 설명",
               "keyReviewPoints": ["반드시 기억할 포인트 (최소 1개)"],
               "priority": "VERY_HIGH | HIGH | MEDIUM"
             }]}
            topicReference 는 요청에 제시된 값만 쓴다.
            """;

    private final GeminiTextClient client;

    public GeminiWrongAnswerSummaryClient(GeminiTextClient client) {
        this.client = client;
    }

    @Override
    public AiWrongAnswerSummaryResult generate(AiWrongAnswerSummaryRequest request) {
        try {
            return client.generate(
                    WrongAnswerSummaryPrompts.summarySystemPrompt(),
                    WrongAnswerSummaryPrompts.summaryUserMessage(request) + OUTPUT_FORMAT,
                    AiWrongAnswerSummaryResult.class,
                    "wrong-answer summary (" + request.topics().size() + " topics)");
        } catch (WrongAnswerSummaryGenerationFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new WrongAnswerSummaryGenerationFailedException("ai summary call failed", e);
        }
    }
}
