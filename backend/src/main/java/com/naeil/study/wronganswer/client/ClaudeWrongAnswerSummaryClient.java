package com.naeil.study.wronganswer.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerSummaryRequest;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerSummaryResult;
import com.naeil.study.wronganswer.exception.WrongAnswerSummaryGenerationFailedException;
import com.naeil.study.wronganswer.prompt.WrongAnswerSummaryPrompts;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 프로젝트 공통 AI 스택(Anthropic) 기반 오답 요약 클라이언트.
 *
 * <p>기획 문서는 Gemini 를 전제하지만 이 저장소의 AI 스택은 처음부터 Anthropic SDK 다
 * (PROCESS.md 의 판단 기록 참고). "기존 AI 클라이언트 설정을 재사용한다"는 원칙을 정본으로
 * 채택했고, 공급자를 바꾸려면 {@link AiWrongAnswerSummaryClient} 구현체만 교체하면 된다.
 *
 * <p>분석·퀴즈 클라이언트와 같은 방식이다. 구조화 출력을 쓰고, 재시도는 SDK가 담당한다.
 */
public class ClaudeWrongAnswerSummaryClient implements AiWrongAnswerSummaryClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeWrongAnswerSummaryClient.class);

    /** Topic 여러 개의 요약이 잘리지 않을 만큼. */
    private static final long MAX_TOKENS = 12_000L;

    private final AnthropicClient client;
    private final String model;

    public ClaudeWrongAnswerSummaryClient(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public AiWrongAnswerSummaryResult generate(AiWrongAnswerSummaryRequest request) {
        StructuredMessageCreateParams<AiWrongAnswerSummaryResult> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(WrongAnswerSummaryPrompts.summarySystemPrompt())
                .outputConfig(AiWrongAnswerSummaryResult.class)
                .addUserMessage(WrongAnswerSummaryPrompts.summaryUserMessage(request))
                .build();

        // 프롬프트 전문과 응답 전문은 로그로 남기지 않는다. 강의자료와 학습 맥락이 그대로 찍힌다.
        long startedAt = System.nanoTime();
        try {
            Optional<AiWrongAnswerSummaryResult> parsed = client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst();

            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("ai wrong-answer summary call finished: topics={}, model={}, elapsedMs={}",
                    request.topics().size(), model, elapsedMs);

            return parsed.orElseThrow(() ->
                    new WrongAnswerSummaryGenerationFailedException("ai returned no structured content"));
        } catch (AnthropicServiceException e) {
            log.warn("ai wrong-answer summary call failed: model={}, error={}",
                    model, e.getClass().getSimpleName());
            throw new WrongAnswerSummaryGenerationFailedException("ai summary call failed", e);
        } catch (WrongAnswerSummaryGenerationFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("ai wrong-answer summary call failed unexpectedly: error={}",
                    e.getClass().getSimpleName());
            throw new WrongAnswerSummaryGenerationFailedException("ai summary call failed", e);
        }
    }
}
