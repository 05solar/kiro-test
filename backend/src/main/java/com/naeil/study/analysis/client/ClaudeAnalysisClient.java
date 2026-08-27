package com.naeil.study.analysis.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.naeil.study.analysis.client.dto.AiChunkAnalysisRequest;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicCandidates;
import com.naeil.study.analysis.client.dto.AiTopicMergeRequest;
import com.naeil.study.analysis.exception.AiAnalysisException;
import com.naeil.study.analysis.prompt.AnalysisPrompts;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claude 기반 분석 클라이언트.
 *
 * <p><b>구조화 출력을 쓴다.</b> 자연어 응답을 정규식으로 뜯지 않는다.
 * 응답 타입을 클래스로 넘기면 SDK가 스키마를 만들고 검증된 객체로 되돌려 준다.
 *
 * <p>실패는 모두 {@link AiAnalysisException}으로 감싼다. SDK 예외가 도메인까지 올라오면
 * 공급자를 바꿀 때 도메인 코드도 함께 고쳐야 한다.
 *
 * <p>재시도는 SDK가 담당한다(연결 오류, 429, 5xx). 응답이 규칙에 맞지 않는 경우는
 * 여기서 재시도하지 않는다. 같은 요청을 반복해 봐야 결과가 달라질 이유가 없고 비용만 든다.
 * 그런 경우의 한 번짜리 재시도는 상위 서비스가 판단한다.
 */
public class ClaudeAnalysisClient implements AiAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAnalysisClient.class);

    /** 응답이 잘리지 않을 만큼 넉넉하게. 스트리밍 없이 쓰는 값이라 과하게 크게 잡지 않는다. */
    private static final long MAX_TOKENS = 16_000L;

    private final AnthropicClient client;
    private final String model;

    public ClaudeAnalysisClient(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public AiTopicCandidates analyzeChunk(AiChunkAnalysisRequest request) {
        StructuredMessageCreateParams<AiTopicCandidates> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(AnalysisPrompts.chunkAnalysisSystemPrompt())
                .outputConfig(AiTopicCandidates.class)
                .addUserMessage(AnalysisPrompts.chunkAnalysisUserMessage(request))
                .build();

        return call(params,
                "chunk analysis (%s #%d)".formatted(request.documentReference(), request.chunkIndex()));
    }

    @Override
    public AiTopicAnalysisResult mergeTopics(AiTopicMergeRequest request) {
        StructuredMessageCreateParams<AiTopicAnalysisResult> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(AnalysisPrompts.mergeSystemPrompt())
                .outputConfig(AiTopicAnalysisResult.class)
                .addUserMessage(AnalysisPrompts.mergeUserMessage(request))
                .build();

        return call(params, "topic merge");
    }

    /**
     * 요청을 보내고 구조화된 응답을 꺼낸다.
     *
     * <p>프롬프트 전문과 응답 전문은 로그로 남기지 않는다. 강의자료 내용이 그대로 찍힌다.
     */
    private <T> T call(StructuredMessageCreateParams<T> params, String label) {
        long startedAt = System.nanoTime();
        try {
            Optional<T> parsed = client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst();

            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("ai call finished: step={}, model={}, elapsedMs={}", label, model, elapsedMs);

            return parsed.orElseThrow(() ->
                    new AiAnalysisException("ai returned no structured content: " + label));
        } catch (AnthropicServiceException e) {
            log.warn("ai call failed: step={}, model={}, error={}", label, model, e.getClass().getSimpleName());
            throw new AiAnalysisException("ai call failed: " + label, e);
        } catch (AiAnalysisException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("ai call failed unexpectedly: step={}, error={}", label, e.getClass().getSimpleName());
            throw new AiAnalysisException("ai call failed: " + label, e);
        }
    }

}
