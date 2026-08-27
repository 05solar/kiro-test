package com.naeil.study.analysis.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.naeil.study.analysis.client.AiAnalysisClient;
import com.naeil.study.analysis.client.ClaudeAnalysisClient;
import com.naeil.study.analysis.client.GeminiAnalysisClient;
import com.naeil.study.analysis.client.UnavailableAiAnalysisClient;
import com.naeil.study.common.ai.GeminiTextClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 클라이언트 설정.
 *
 * <p>API 키는 코드에 두지 않고 환경변수로 받는다. 키가 없으면 실패를 미루는 구현체를
 * 대신 등록한다. 개발과 테스트에서 키 없이도 나머지 기능을 쓸 수 있어야 하기 때문이다.
 *
 * <p>공급자는 {@code ai.provider} 로 고른다(기본 anthropic). 도메인 코드는
 * {@link AiAnalysisClient} 만 알기 때문에 공급자를 바꿔도 분석 로직은 그대로다.
 *
 * <p>타임아웃과 재시도는 클라이언트 계층에 넘긴다. 외부 호출을 무한정 기다리지 않는다.
 */
@Configuration
public class AiClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AiClientConfig.class);

    @Bean
    public AiAnalysisClient aiAnalysisClient(
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
                log.warn("ai.gemini.api-key is not configured. AI analysis will fail until it is set.");
                return new UnavailableAiAnalysisClient();
            }
            log.info("ai analysis client configured: provider=gemini, model={}, timeoutSeconds={}, maxRetries={}",
                    geminiModel, timeoutSeconds, maxRetries);
            return new GeminiAnalysisClient(
                    new GeminiTextClient(geminiApiKey, geminiModel, timeoutSeconds, maxRetries));
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ai.api-key is not configured. AI analysis will fail until it is set.");
            return new UnavailableAiAnalysisClient();
        }
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .build();
        log.info("ai analysis client configured: provider=anthropic, model={}, timeoutSeconds={}, maxRetries={}",
                model, timeoutSeconds, maxRetries);
        return new ClaudeAnalysisClient(client, model);
    }
}
