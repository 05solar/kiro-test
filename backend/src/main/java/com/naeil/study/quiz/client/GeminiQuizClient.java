package com.naeil.study.quiz.client;

import com.naeil.study.common.ai.GeminiTextClient;
import com.naeil.study.quiz.client.dto.AiQuizGenerationRequest;
import com.naeil.study.quiz.client.dto.AiQuizGenerationResult;
import com.naeil.study.quiz.exception.QuizGenerationFailedException;
import com.naeil.study.quiz.prompt.QuizPrompts;

/**
 * Gemini 기반 퀴즈 생성 클라이언트.
 *
 * <p>프롬프트는 Claude 구현과 같다({@link QuizPrompts}). 기대하는 JSON 구조만
 * 사용자 메시지에 덧붙이고, 응답 값 검증은 기존 {@code AiQuizResponseValidator} 가 담당한다.
 */
public class GeminiQuizClient implements AiQuizClient {

    private static final String OUTPUT_FORMAT = """

            # OUTPUT FORMAT (JSON only)
            {"questions": [{
              "question": "문제 본문 (최대 2000자)",
              "options": ["보기1", "보기2", "보기3", "보기4"],
              "correctIndex": 0,
              "explanation": "정답 해설",
              "difficulty": "EASY | MEDIUM | HARD"
            }]}
            options 는 정확히 4개, correctIndex 는 0~3 이어야 한다.
            """;

    private final GeminiTextClient client;

    public GeminiQuizClient(GeminiTextClient client) {
        this.client = client;
    }

    @Override
    public AiQuizGenerationResult generate(AiQuizGenerationRequest request) {
        try {
            return client.generate(
                    QuizPrompts.generationSystemPrompt(request),
                    QuizPrompts.generationUserMessage(request) + OUTPUT_FORMAT,
                    AiQuizGenerationResult.class,
                    "quiz generation (" + request.topicTitle() + ")");
        } catch (QuizGenerationFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QuizGenerationFailedException("ai quiz call failed", e);
        }
    }
}
