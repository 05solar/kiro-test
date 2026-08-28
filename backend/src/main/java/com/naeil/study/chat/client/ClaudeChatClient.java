package com.naeil.study.chat.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.naeil.study.chat.client.dto.AiChatAnswer;
import com.naeil.study.chat.client.dto.AiChatRequest;
import com.naeil.study.chat.exception.ChatFailedException;
import com.naeil.study.chat.prompt.ChatPrompts;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claude 기반 학습 챗봇 클라이언트.
 *
 * <p>분석·퀴즈 클라이언트와 같은 방식이다. <b>구조화 출력을 쓴다.</b> 자연어 응답을 그대로
 * 쓰면 모델이 앞뒤에 붙이는 인사말을 화면에서 다시 걷어내야 한다.
 *
 * <p><b>지난 대화를 여러 개의 메시지로 나눠 보내지 않는다.</b> 사용자 메시지 하나 안의
 * {@code <conversation>} 태그로 넣는다. SDK 의 멀티턴 자리에 넣으면 저장된 대화가
 * 모델에게 "실제로 주고받은 것"으로 취급되어, 그 안의 문장이 지시문처럼 읽힐 여지가 커진다.
 * 태그 안의 데이터로 두면 주입 방어 규칙이 그대로 적용된다.
 *
 * <p>재시도는 SDK 가 담당한다(연결 오류, 429, 5xx). 응답 형식 오류는 재시도하지 않는다.
 */
public class ClaudeChatClient implements AiChatClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeChatClient.class);

    /** 답변은 몇 문장이다. 분석·퀴즈보다 훨씬 작게 잡는다. */
    private static final long MAX_TOKENS = 2_000L;

    private final AnthropicClient client;
    private final String model;

    public ClaudeChatClient(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public AiChatAnswer answer(AiChatRequest request) {
        StructuredMessageCreateParams<AiChatAnswer> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(ChatPrompts.systemPrompt(request.context()))
                .outputConfig(AiChatAnswer.class)
                .addUserMessage(ChatPrompts.userMessage(request))
                .build();

        // 질문 본문도 답변 본문도 로그에 남기지 않는다. 사용자가 약점을 그대로 적는 자리다.
        long startedAt = System.nanoTime();
        try {
            Optional<AiChatAnswer> parsed = client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst();

            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("ai chat call finished: model={}, elapsedMs={}", model, elapsedMs);

            return parsed.orElseThrow(() -> new ChatFailedException("ai returned no structured content"));
        } catch (AnthropicServiceException e) {
            log.warn("ai chat call failed: model={}, error={}", model, e.getClass().getSimpleName());
            throw new ChatFailedException("ai chat call failed", e);
        } catch (ChatFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("ai chat call failed unexpectedly: error={}", e.getClass().getSimpleName());
            throw new ChatFailedException("ai chat call failed", e);
        }
    }
}
