package com.naeil.study.analysis.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.naeil.study.analysis.client.AiAnalysisClient;
import com.naeil.study.analysis.client.ClaudeAnalysisClient;
import com.naeil.study.analysis.client.GeminiAnalysisClient;
import com.naeil.study.analysis.client.MockAiAnalysisClient;
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
    /**
     * 분석 방식.
     *
     * <pre>
     * mock    AI 를 부르지 않고 목 데이터를 만든다. 과금되지 않는다
     * gemini  실제 AI 를 부른다. ai.provider 설정을 따른다
     * </pre>
     *
     * <p><b>기본값이 mock 인 이유</b> — 분석은 자료 조각 수만큼 AI 를 부른다.
     * 화면을 한 번 확인할 때마다 그만큼 과금된다.
     * 실제 배포에서는 {@code LLM_MODE=gemini} 를 넣는다(docker-compose 에 이미 들어 있다).
     */
    public AiAnalysisClient aiAnalysisClient(
            @Value("${ai.mode:mock}") String aiMode,
            @Value("${ai.provider:anthropic}") String provider,
            @Value("${ai.api-key:}") String apiKey,
            @Value("${ai.model:claude-opus-5}") String model,
            @Value("${ai.gemini.api-key:}") String geminiApiKey,
            @Value("${ai.gemini.model:gemini-3.5-flash-lite}") String geminiModel,
            @Value("${ai.timeout-seconds:180}") long timeoutSeconds,
            @Value("${ai.max-retries:2}") int maxRetries
    ) {
        if ("mock".equalsIgnoreCase(aiMode)) {
            // 눈에 띄게 남긴다. 목 데이터가 나오는데 원인을 모르면 한참 헤맨다.
            log.warn("ai.mode=mock — 강의자료를 AI 로 분석하지 않고 목 데이터를 돌려준다. "
                    + "실제 분석이 필요하면 LLM_MODE=gemini 로 실행한다.");
            return new MockAiAnalysisClient();
        }

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
