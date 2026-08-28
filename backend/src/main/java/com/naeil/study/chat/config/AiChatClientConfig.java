package com.naeil.study.chat.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.naeil.study.chat.client.AiChatClient;
import com.naeil.study.chat.client.ClaudeChatClient;
import com.naeil.study.chat.client.GeminiChatClient;
import com.naeil.study.chat.client.MockAiChatClient;
import com.naeil.study.chat.client.UnavailableAiChatClient;
import com.naeil.study.common.ai.GeminiTextClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 챗봇 클라이언트 설정.
 *
 * <p>분석·퀴즈 클라이언트와 같은 {@code ai.*} 설정을 재사용한다. 공급자는 {@code ai.provider}
 * 로 고른다. 키가 없으면 실패를 미루는 구현체를 등록해 챗봇과 무관한 기능은 계속 동작하게 한다.
 *
 * <p>타임아웃은 분석·퀴즈보다 짧게 잡는다. 챗봇은 사용자가 화면 앞에서 기다리는 기능이라,
 * 3분을 기다리게 하느니 실패를 알리고 다시 묻게 하는 편이 낫다.
 */
@Configuration
public class AiChatClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AiChatClientConfig.class);

    /**
     * 챗봇 응답 방식.
     *
     * <pre>
     * mock    AI 를 부르지 않고 목 응답을 만든다. 개발·UI 확인용이며 과금되지 않는다.
     * gemini  실제 AI 를 부른다. ai.provider 설정을 따른다.
     * </pre>
     *
     * <p><b>기본값이 mock 인 이유</b> — 챗봇은 이 프로젝트에서 가장 자주 눌리는 기능이다.
     * 화면을 한 번 확인하는 동안 질문을 서너 개 던지게 되고, 그때마다 과금된다.
     * 실제 배포에서는 {@code LLM_MODE=gemini} 를 넣는다(docker-compose 에 이미 들어 있다).
     */
    @Bean
    public AiChatClient aiChatClient(
            @Value("${chat.ai-mode:mock}") String aiMode,
            @Value("${ai.provider:anthropic}") String provider,
            @Value("${ai.api-key:}") String apiKey,
            @Value("${ai.model:claude-opus-5}") String model,
            @Value("${ai.gemini.api-key:}") String geminiApiKey,
            @Value("${ai.gemini.model:gemini-3.5-flash-lite}") String geminiModel,
            @Value("${chat.timeout-seconds:45}") long timeoutSeconds,
            @Value("${ai.max-retries:2}") int maxRetries
    ) {
        if ("mock".equalsIgnoreCase(aiMode)) {
            // 눈에 띄게 남긴다. 목 응답이 나오는데 원인을 모르면 한참 헤맨다.
            log.warn("chat.ai-mode=mock — 챗봇이 AI 를 부르지 않고 목 응답을 돌려준다. "
                    + "실제 답변이 필요하면 LLM_MODE=gemini 로 실행한다.");
            return new MockAiChatClient();
        }

        if ("gemini".equalsIgnoreCase(provider)) {
            if (geminiApiKey == null || geminiApiKey.isBlank()) {
                log.warn("ai.gemini.api-key is not configured. study chat will fail until it is set.");
                return new UnavailableAiChatClient();
            }
            log.info("ai chat client configured: provider=gemini, model={}, timeoutSeconds={}, maxRetries={}",
                    geminiModel, timeoutSeconds, maxRetries);
            return new GeminiChatClient(
                    new GeminiTextClient(geminiApiKey, geminiModel, timeoutSeconds, maxRetries));
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ai.api-key is not configured. study chat will fail until it is set.");
            return new UnavailableAiChatClient();
        }
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .build();
        log.info("ai chat client configured: provider=anthropic, model={}, timeoutSeconds={}, maxRetries={}",
                model, timeoutSeconds, maxRetries);
        return new ClaudeChatClient(client, model);
    }
}
