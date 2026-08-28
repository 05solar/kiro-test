package com.naeil.study.quiz.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.naeil.study.common.ai.GeminiTextClient;
import com.naeil.study.quiz.client.AiQuizClient;
import com.naeil.study.quiz.client.ClaudeQuizClient;
import com.naeil.study.quiz.client.GeminiQuizClient;
import com.naeil.study.quiz.client.MockAiQuizClient;
import com.naeil.study.quiz.client.UnavailableAiQuizClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 퀴즈 클라이언트 설정.
 *
 * <p>분석 클라이언트({@code AiClientConfig})와 같은 {@code ai.*} 설정을 재사용한다.
 * 공급자는 {@code ai.provider} 로 고른다(기본 anthropic).
 * 키가 없으면 실패를 미루는 구현체를 대신 등록해 퀴즈와 무관한 기능은 계속 동작하게 한다.
 */
@Configuration
public class AiQuizClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AiQuizClientConfig.class);

    /**
     * 퀴즈 생성 방식.
     *
     * <pre>
     * mock    AI 를 부르지 않고 목 데이터를 만든다. 개발·UI 확인용이며 과금되지 않는다.
     * gemini  실제 AI 를 부른다. ai.provider 설정을 따른다.
     * </pre>
     *
     * <p><b>기본값이 mock 인 이유</b> — "새로운 퀴즈 만들기"처럼 여러 번 눌러 보게 되는 기능은
     * 화면을 한 번 확인할 때마다 과금된다. 실수로 켜져 있는 쪽이 실수로 꺼져 있는 쪽보다 비싸다.
     * 실제 배포에서는 {@code QUIZ_AI_MODE=gemini} 를 넣는다(docker-compose 에 이미 들어 있다).
     */
    @Bean
    public AiQuizClient aiQuizClient(
            @Value("${quiz.ai-mode:mock}") String aiMode,
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
            log.warn("quiz.ai-mode=mock — 퀴즈를 AI 로 만들지 않고 목 데이터를 돌려준다. "
                    + "실제 문제가 필요하면 QUIZ_AI_MODE=gemini 로 실행한다.");
            return new MockAiQuizClient();
        }

        if ("gemini".equalsIgnoreCase(provider)) {
            if (geminiApiKey == null || geminiApiKey.isBlank()) {
                log.warn("ai.gemini.api-key is not configured. quiz generation will fail until it is set.");
                return new UnavailableAiQuizClient();
            }
            log.info("ai quiz client configured: provider=gemini, model={}, timeoutSeconds={}, maxRetries={}",
                    geminiModel, timeoutSeconds, maxRetries);
            return new GeminiQuizClient(
                    new GeminiTextClient(geminiApiKey, geminiModel, timeoutSeconds, maxRetries));
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ai.api-key is not configured. quiz generation will fail until it is set.");
            return new UnavailableAiQuizClient();
        }
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .build();
        log.info("ai quiz client configured: provider=anthropic, model={}, timeoutSeconds={}, maxRetries={}",
                model, timeoutSeconds, maxRetries);
        return new ClaudeQuizClient(client, model);
    }
}
