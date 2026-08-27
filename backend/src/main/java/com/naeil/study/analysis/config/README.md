# analysis/config

| 클래스 | 역할 |
| --- | --- |
| `AiClientConfig` | `AiAnalysisClient` 빈 등록 |

## 키가 없으면 실패를 미룬다

```java
if (apiKey.isBlank()) {
    return new UnavailableAiAnalysisClient();
}
```

키가 없다고 애플리케이션이 뜨지 않으면 분석과 무관한 기능까지 막힌다.
세션, 업로드, 파싱은 AI 없이도 동작해야 한다.
분석을 실제로 요청한 순간에만 502로 실패한다.

## 조건부 애노테이션을 쓰지 않는 이유

`@ConditionalOnProperty` 나 `@ConditionalOnBean` 으로 두 빈을 두면
어느 쪽이 등록되는지가 평가 순서에 달린다. 메서드 하나에서 분기하면 흐름이 그대로 보인다.

## 설정

| 프로퍼티 | 환경변수 | 기본값 |
| --- | --- | --- |
| `ai.api-key` | `AI_API_KEY` | (없음) |
| `ai.model` | `AI_MODEL` | `claude-opus-5` |
| `ai.timeout-seconds` | `AI_TIMEOUT_SECONDS` | `180` |
| `ai.max-retries` | `AI_MAX_RETRIES` | `2` |

타임아웃과 재시도는 SDK 설정으로 넘긴다. 외부 호출을 무한정 기다리지 않는다.
