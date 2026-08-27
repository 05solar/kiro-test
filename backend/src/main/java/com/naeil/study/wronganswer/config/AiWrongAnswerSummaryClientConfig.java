package com.naeil.study.wronganswer.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.naeil.study.common.ai.GeminiTextClient;
import com.naeil.study.wronganswer.client.AiWrongAnswerSummaryClient;
import com.naeil.study.wronganswer.client.ClaudeWrongAnswerSummaryClient;
import com.naeil.study.wronganswer.client.GeminiWrongAnswerSummaryClient;
import com.naeil.study.wronganswer.client.UnavailableAiWrongAnswerSummaryClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 오답 요약 클라이언트 설정.
 *
 * <p>분석·퀴즈 클라이언트와 같은 {@code ai.*} 설정을 재사용한다.
 * 공급자는 {@code ai.provider} 로 고른다(기본 anthropic).
 */
@Configuration
public class AiWrongAnswerSummaryClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AiWrongAnswerSummaryClientConfig.class);

    @Bean
    public AiWrongAnswerSummaryClient aiWrongAnswerSummaryClient(
            @Value("${ai.provider:anthropic}") String provider,
            @Value("${ai.api-key:}") String apiKey,
            @Value("${ai.model:claude-opus-5}") String model,
            @Value("${ai.gemini.api-key:}") String geminiApiKey,
            @Value("${ai.gemini.model:gemini-3.5-flash-lite}") String geminiModel,
            @Value("${ai.timeout-seconds:180}") long timeoutSeconds,
            @Value("${ai.max-retries:2}") int maxRetries
    ) {
        if ("gemini".equalsIgnoreCase(provider)) {
            if (geminiApiKey == null || geminiApiKey.isBlank()) {
                log.warn("ai.gemini.api-key is not configured. wrong-answer summary will fail until it is set.");
                return new UnavailableAiWrongAnswerSummaryClient();
            }
            log.info("ai wrong-answer summary client configured: provider=gemini, model={}, "
                    + "timeoutSeconds={}, maxRetries={}", geminiModel, timeoutSeconds, maxRetries);
            return new GeminiWrongAnswerSummaryClient(
                    new GeminiTextClient(geminiApiKey, geminiModel, timeoutSeconds, maxRetries));
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ai.api-key is not configured. wrong-answer summary will fail until it is set.");
            return new UnavailableAiWrongAnswerSummaryClient();
        }
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .build();
        log.info("ai wrong-answer summary client configured: provider=anthropic, model={}, "
                + "timeoutSeconds={}, maxRetries={}", model, timeoutSeconds, maxRetries);
        return new ClaudeWrongAnswerSummaryClient(client, model);
    }
}
