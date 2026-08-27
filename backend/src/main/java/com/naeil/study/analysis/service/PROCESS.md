# analysis/service 작업 절차

## 흐름을 바꿀 때

```
1. AI 호출이 트랜잭션 밖에 있는지 확인한다
2. 상태 변경은 AnalysisStateWriter 에만 둔다
3. 새 실패 지점이 ANALYSIS_FAILED 로 이어지는지 확인한다
4. FakeAiAnalysisClient 로 테스트를 추가한다
```

## 테스트 작성 방식

`AnalysisStateWriter` 를 목으로 두고 오케스트레이션만 검증한다.
AI는 `FakeAiAnalysisClient` 로 대체한다.

```java
given(stateWriter.beginAnalysis(SESSION_ID)).willReturn(target);
aiClient.failMerge(new AiAnalysisException("ai call failed"));
// verify(stateWriter).failAnalysis(SESSION_ID);
```

세션 `id` 는 JPA가 채우므로 단위 테스트에서는 리플렉션으로 넣는다.

## STEP 7 (커리큘럼) 에서

Topic이 만들어진 뒤 커리큘럼을 생성한다. 이 서비스를 고치지 않고 별도 도메인으로 만든다.
재분석 시 커리큘럼을 어떻게 할지는 그때 정한다. 현재는 커리큘럼이 없어 영향이 없다.

## 검증

```bash
./gradlew test --tests "*AnalysisServiceTest*" --tests "*AnalysisApiIntegrationTest*"
```
