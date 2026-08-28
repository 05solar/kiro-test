package com.naeil.study.chat.client;

import com.naeil.study.chat.client.dto.AiChatAnswer;
import com.naeil.study.chat.client.dto.AiChatRequest;
import com.naeil.study.chat.exception.ChatFailedException;
import com.naeil.study.chat.prompt.ChatPrompts;
import com.naeil.study.common.ai.GeminiTextClient;

/**
 * Gemini 기반 학습 챗봇 클라이언트.
 *
 * <p>프롬프트는 Claude 구현과 같다({@link ChatPrompts}). 기대하는 JSON 구조만
 * 사용자 메시지에 덧붙인다.
 *
 * <p>로그에 남기는 것은 호출 이름뿐이다. 질문 본문도 답변 본문도 남기지 않는다.
 * 사용자가 시험 범위와 약점을 그대로 적는 자리라, 로그에 남으면 그게 곧 유출이다.
 */
public class GeminiChatClient implements AiChatClient {

    private static final String OUTPUT_FORMAT = """

            # OUTPUT FORMAT (JSON only)
            {"answer": "답변 본문", "answeredFromMaterial": true}
            answer 는 줄바꿈을 포함할 수 있다. 두 필드를 모두 채운다.
            """;

    private final GeminiTextClient client;

    public GeminiChatClient(GeminiTextClient client) {
        this.client = client;
    }

    @Override
    public AiChatAnswer answer(AiChatRequest request) {
        try {
            return client.generate(
                    ChatPrompts.systemPrompt(request.context()),
                    ChatPrompts.userMessage(request) + OUTPUT_FORMAT,
                    AiChatAnswer.class,
                    "study chat");
        } catch (ChatFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ChatFailedException("ai chat call failed", e);
        }
    }
}
