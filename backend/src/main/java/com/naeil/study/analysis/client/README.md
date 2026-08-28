# analysis/client

AI 호출 추상화와 구현체.

| 클래스 | 역할 |
| --- | --- |
| `AiAnalysisClient` | 인터페이스. 도메인은 이것만 안다 |
| `ClaudeAnalysisClient` | Claude 구현체 (Anthropic Java SDK) |
| `GeminiAnalysisClient` | Gemini 구현체 |
| `MockAiAnalysisClient` | `ai.mode=mock` 일 때 등록되는 구현체. AI 를 부르지 않는다 |
| `UnavailableAiAnalysisClient` | API 키가 없을 때 등록되는 구현체 |
| `dto/` | 요청/응답 모델 |

## 인터페이스

```java
AiTopicCandidates     analyzeChunk(AiChunkAnalysisRequest request);
AiTopicAnalysisResult mergeTopics(AiTopicMergeRequest request);
AiTopicAnalysisResult generateFromGeneralKnowledge(AiGeneralKnowledgeRequest request);
```

앞의 둘은 강의자료에서 뽑는 경로다. 두 단계인 이유는 상위 `README.md` 참고.

세 번째는 **자료가 하나도 없을 때**의 경로다. 나눌 자료가 없으므로 조각 분석을 하지 않고
과목명·시험 범위·학습 맥락만으로 한 번에 만든다. 이 경로로 만든 Topic 에는 근거로 삼을
문서가 없으므로, AI 가 문서 ID 를 지어내도 검증 단계에서 전부 버린다.

## 구조화 출력

자연어 응답을 정규식으로 뜯지 않는다. 응답 타입을 클래스로 넘기면 SDK가 스키마를 만들고
검증된 객체로 되돌려 준다.

```java
MessageCreateParams.builder()
    .outputConfig(AiTopicCandidates.class)
```

응답 record의 필드는 감싼 타입(`Integer`, `Boolean`)으로 둔다.
AI가 값을 빠뜨렸을 때 0이나 false로 조용히 채워지지 않고 검증 단계에서 드러나게 하기 위해서다.

## 키가 없어도 기동한다

키가 없다고 애플리케이션이 뜨지 않으면 분석과 무관한 기능까지 막힌다.
세션, 업로드, 파싱은 AI 없이도 동작해야 하므로 기동은 허용하고,
분석을 실제로 요청한 순간에만 `UnavailableAiAnalysisClient` 가 실패시킨다.

## 실패 처리

SDK 예외를 그대로 올리지 않는다. 모두 `AiAnalysisException` 으로 감싼다.
그러지 않으면 공급자를 바꿀 때 도메인 코드도 함께 고쳐야 한다.

재시도는 SDK가 담당한다(연결 오류, 429, 5xx). 응답이 규칙에 맞지 않는 경우는
여기서 재시도하지 않는다. 그 판단은 상위 서비스가 한다.

## dto

| 방향 | 클래스 |
| --- | --- |
| 요청 | `AiChunkAnalysisRequest`, `AiTopicMergeRequest`, `AiStudyContext`, `AiDocumentReference`, `AiSourcedTopicCandidate` |
| 응답 | `AiTopicCandidates`, `AiTopicCandidate`, `AiTopicAnalysisResult`, `AiTopicResult` |

`AiDocumentReference` 는 AI용 참조값(`DOC_1`)과 실제 UUID를 함께 들고 있다.
AI에게는 참조값만 보여주고, 응답에 돌아온 값을 서버가 UUID로 되돌린다.
