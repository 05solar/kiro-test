# analysis 작업 절차

## 분석 흐름을 바꿀 때

```
1. 트랜잭션 경계를 먼저 본다
   AI 호출은 트랜잭션 밖에서 한다 (AnalysisService 에는 @Transactional 이 없다)
2. 상태 변경은 AnalysisStateWriter 의 짧은 트랜잭션으로 한다
3. 실패 경로를 함께 설계한다 (어느 시점 실패든 ANALYSIS_FAILED 로 끝나야 한다)
4. FakeAiAnalysisClient 로 서비스 테스트 작성
5. docs/api/analysis-api.md 갱신
```

## 프롬프트를 바꿀 때

```
1. 영역 구분(SYSTEM RULES / TASK / 데이터)을 유지한다
2. 자료와 학습 맥락은 태그로 감싼 채로 둔다
3. 출력 형식을 바꾸면 응답 record 와 검증기를 함께 고친다
4. 품질 확인은 실제 자료로 수동 검증한다. 자동 테스트로는 문구 변화를 잡을 수 없다
```

## AI 공급자를 추가할 때

```
1. AiAnalysisClient 구현체 작성
2. 실패를 AiAnalysisException 으로 감싼다 (SDK 예외를 올리지 않는다)
3. config/AiClientConfig 에서 설정값으로 고르게 한다
4. 기존 서비스 테스트가 그대로 통과하는지 확인한다 (인터페이스만 쓰므로 통과해야 한다)
```

## 비동기로 옮길 때 (필요해지면)

현재는 동기다. 옮길 때 손대는 지점은 다음과 같다.

```
AnalysisController      요청을 받고 202 를 즉시 반환
AnalysisService.analyze 별도 실행자에서 수행
세션 status             ANALYZING 을 진행 표시로 사용 (이미 그렇게 동작한다)
```

`AnalysisStateWriter` 로 상태 변경을 분리해 두었기 때문에 트랜잭션 구조는 그대로 쓸 수 있다.

## 검증

```bash
./gradlew test --tests "*Analysis*" --tests "*DocumentChunker*" --tests "*Validator*"
./gradlew build
```

테스트는 실제 AI API를 부르지 않는다. `FakeAiAnalysisClient` 를 쓴다.
