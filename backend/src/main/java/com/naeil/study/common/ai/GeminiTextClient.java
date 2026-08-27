package com.naeil.study.common.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gemini {@code generateContent} REST 호출.
 *
 * <p>여러 도메인(분석 / 퀴즈 / 오답 요약)의 Gemini 구현체가 함께 쓴다. SDK 를 추가하지 않고
 * REST 를 직접 부른다 — 현재 Gemini Java SDK 는 API 가 유동적이고, 이 프로젝트가 필요한 것은
 * "시스템 프롬프트 + 사용자 메시지 → JSON 응답" 하나뿐이다.
 *
 * <p><b>구조화 출력.</b> {@code responseMimeType=application/json} 으로 JSON 만 받게 하고,
 * 기대하는 필드 구조는 각 도메인 클라이언트가 사용자 메시지에 명시한다. 응답은 Jackson 으로
 * 해당 DTO 레코드에 매핑하며, 값 검증은 기존 도메인 Validator 가 그대로 담당한다.
 *
 * <p>재시도는 연결 오류·429·5xx 에만 한다. 응답 형식 오류는 재시도하지 않는다
 * (Anthropic 클라이언트와 같은 정책).
 *
 * <p>API 키는 로그·예외 메시지에 절대 넣지 않는다.
 */
public class GeminiTextClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiTextClient.class);

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    /**
     * 신형 Gemini 모델은 내부 사고(thinking) 토큰도 이 한도에서 소모한다.
     * 낮게 잡으면 사고만 하다가 본문 JSON 이 잘린 채 끝난다.
     */
    private static final long MAX_OUTPUT_TOKENS = 32_768L;
    private static final double TEMPERATURE = 0.3;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxRetries;

    public GeminiTextClient(String apiKey, String model, long timeoutSeconds, int maxRetries) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxRetries = maxRetries;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 시스템 프롬프트와 사용자 메시지를 보내고 JSON 응답을 지정한 타입으로 받는다.
     *
     * @param label 로그용 호출 이름. 프롬프트 내용은 로그에 남기지 않는다
     * @throws GeminiClientException 호출 실패, 빈 응답, JSON 매핑 실패
     */
    public <T> T generate(String systemPrompt, String userMessage, Class<T> type, String label) {
        String body = requestBody(systemPrompt, userMessage);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT.formatted(model)))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        long startedAt = System.nanoTime();
        HttpResponse<String> response = sendWithRetry(request, label);
        String text = extractText(response.body(), label);

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("gemini call finished: step={}, model={}, elapsedMs={}", label, model, elapsedMs);

        try {
            return objectMapper.readValue(text, type);
        } catch (IOException e) {
            // 모델이 {"topics":[...]} 대신 최상위 배열 [...]로 답하는 경우가 실제로 관측됐다.
            // 필드가 하나뿐인 응답 타입이면 그 필드로 감싸서 한 번 더 시도한다.
            T recovered = tryWrapBareArray(text, type);
            if (recovered != null) {
                log.info("gemini bare-array response recovered: step={}", label);
                return recovered;
            }
            // 어떤 모양으로 깨졌는지 진단할 수 있게 앞부분만 남긴다. 전문은 남기지 않는다.
            log.warn("gemini unparsable json: step={}, head={}",
                    label, text.substring(0, Math.min(160, text.length())).replaceAll("\\s+", " "));
            throw new GeminiClientException("gemini returned unparsable json: " + label, e);
        }
    }

    /**
     * 최상위 배열 응답을 단일 필드 레코드로 복구한다. 해당하지 않으면 null.
     */
    private <T> T tryWrapBareArray(String text, Class<T> type) {
        String trimmed = text.strip();
        if (!trimmed.startsWith("[") || !type.isRecord()
                || type.getRecordComponents().length != 1) {
            return null;
        }
        try {
            ObjectNode wrapped = objectMapper.createObjectNode();
            wrapped.set(type.getRecordComponents()[0].getName(), objectMapper.readTree(trimmed));
            return objectMapper.treeToValue(wrapped, type);
        } catch (IOException e) {
            return null;
        }
    }

    private String requestBody(String systemPrompt, String userMessage) {
        ObjectNode root = objectMapper.createObjectNode();

        ObjectNode systemInstruction = root.putObject("systemInstruction");
        systemInstruction.putArray("parts").addObject().put("text", systemPrompt);

        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", userMessage);

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", TEMPERATURE);
        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS);

        return root.toString();
    }

    /**
     * 연결 오류·429·5xx 에 한해 재시도한다. 4xx(429 제외)는 요청 자체의 문제라 반복해도 같다.
     *
     * <p><b>429 는 분당 요청 제한이다.</b> 초 단위 백오프로는 다음 1분 창이 열리기 전에
     * 재시도 횟수를 소진한다. 그래서 429 는 15초 × 시도 횟수로 길게 기다리고,
     * 그 밖의 일시 오류(5xx / 연결)는 1초 × 시도 횟수로 짧게 기다린다.
     */
    private HttpResponse<String> sendWithRetry(HttpRequest request, String label) {
        RuntimeException lastError = null;
        long nextBackoffMillis = 0L;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            sleepBeforeRetry(nextBackoffMillis, label);
            try {
                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status == 200) {
                    return response;
                }
                String summary = errorSummary(response.body());
                if (status == 429 || status >= 500) {
                    lastError = new GeminiClientException(
                            "gemini call failed (" + status + "): " + label + " - " + summary);
                    nextBackoffMillis = status == 429
                            ? 15_000L * (attempt + 1)
                            : 1_000L * (attempt + 1);
                    log.warn("gemini call retryable failure: step={}, status={}, attempt={}, backoffMs={}",
                            label, status, attempt + 1, nextBackoffMillis);
                    continue;
                }
                throw new GeminiClientException(
                        "gemini call rejected (" + status + "): " + label + " - " + summary);
            } catch (IOException e) {
                lastError = new GeminiClientException("gemini connection failed: " + label, e);
                nextBackoffMillis = 1_000L * (attempt + 1);
                log.warn("gemini connection failure: step={}, attempt={}, error={}",
                        label, attempt + 1, e.getClass().getSimpleName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GeminiClientException("gemini call interrupted: " + label, e);
            }
        }
        throw lastError == null
                ? new GeminiClientException("gemini call failed: " + label)
                : lastError;
    }

    private void sleepBeforeRetry(long backoffMillis, String label) {
        if (backoffMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeminiClientException("gemini retry interrupted: " + label, e);
        }
    }

    /** 응답에서 첫 candidate 의 텍스트를 모아 돌려준다. */
    private String extractText(String responseBody, String label) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new GeminiClientException(
                        "gemini returned no candidates: " + label + " - " + errorSummary(responseBody));
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode part : candidates.get(0).path("content").path("parts")) {
                // 사고(thinking) 모델은 thought=true 인 사고 요약 파트를 함께 보낸다.
                // 본문에 이어붙이면 JSON 앞뒤에 자연어가 섞여 파싱이 깨진다. 본문만 취한다.
                if (part.path("thought").asBoolean(false)) {
                    continue;
                }
                if (part.hasNonNull("text")) {
                    text.append(part.get("text").asText());
                }
            }
            if (text.isEmpty()) {
                throw new GeminiClientException("gemini returned empty text: " + label);
            }
            return text.toString();
        } catch (IOException e) {
            throw new GeminiClientException("gemini returned unreadable response: " + label, e);
        }
    }

    /** 오류 본문에서 메시지만 짧게 뽑는다. 본문 전체를 예외에 싣지 않는다. */
    private String errorSummary(String responseBody) {
        try {
            JsonNode error = objectMapper.readTree(responseBody).path("error");
            String message = error.path("message").asText("");
            if (message.isEmpty()) {
                return "(no error message)";
            }
            return message.length() > 200 ? message.substring(0, 200) : message;
        } catch (IOException e) {
            return "(unreadable error body)";
        }
    }
}
