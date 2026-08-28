package com.naeil.study.quiz.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.naeil.study.quiz.client.dto.AiQuizGenerationRequest;
import com.naeil.study.quiz.client.dto.AiQuizGenerationResult;
import com.naeil.study.quiz.exception.QuizGenerationFailedException;
import com.naeil.study.quiz.prompt.QuizPrompts;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claude 기반 퀴즈 생성 클라이언트.
 *
 * <p>분석 클라이언트({@code ClaudeAnalysisClient})와 같은 방식이다.
 * <b>구조화 출력을 쓴다.</b> 자연어 응답을 정규식으로 뜯지 않는다.
 *
 * <p>재시도는 SDK가 담당한다(연결 오류, 429, 5xx). 응답이 규칙에 맞지 않는 경우는
 * 여기서 재시도하지 않는다. 정답 검증(self-check)은 별도 호출이 아니라 같은 요청의
 * 시스템 프롬프트에서 요구한다. 호출을 늘리면 비용만 는다.
 */
public class ClaudeQuizClient implements AiQuizClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeQuizClient.class);

    /** 문제 5개 + 해설이 잘리지 않을 만큼. 분석보다 응답이 짧아 크게 잡지 않는다. */
    private static final long MAX_TOKENS = 8_000L;

    private final AnthropicClient client;
    private final String model;

    public ClaudeQuizClient(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public AiQuizGenerationResult generate(AiQuizGenerationRequest request) {
        StructuredMessageCreateParams<AiQuizGenerationResult> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(QuizPrompts.generationSystemPrompt(request))
                .outputConfig(AiQuizGenerationResult.class)
                .addUserMessage(QuizPrompts.generationUserMessage(request))
                .build();

        // 프롬프트 전문과 응답 전문은 로그로 남기지 않는다. 강의자료 내용이 그대로 찍힌다.
        long startedAt = System.nanoTime();
        try {
            Optional<AiQuizGenerationResult> parsed = client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst();

            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("ai quiz call finished: topic={}, model={}, elapsedMs={}",
                    request.topicTitle(), model, elapsedMs);

            return parsed.orElseThrow(() ->
                    new QuizGenerationFailedException("ai returned no structured content"));
        } catch (AnthropicServiceException e) {
            log.warn("ai quiz call failed: model={}, error={}", model, e.getClass().getSimpleName());
            throw new QuizGenerationFailedException("ai quiz call failed", e);
        } catch (QuizGenerationFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("ai quiz call failed unexpectedly: error={}", e.getClass().getSimpleName());
            throw new QuizGenerationFailedException("ai quiz call failed", e);
        }
    }
}
