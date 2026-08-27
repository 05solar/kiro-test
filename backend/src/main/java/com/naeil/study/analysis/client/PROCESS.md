# analysis/client 작업 절차

## 새 공급자를 추가할 때

```
1. AiAnalysisClient 구현체 작성
2. 실패를 AiAnalysisException 으로 감싼다
3. 타임아웃과 재시도를 SDK 설정으로 넘긴다
4. config/AiClientConfig 에서 설정값으로 고르게 한다
5. 기존 서비스 테스트가 그대로 통과하는지 확인한다
```

도메인 테스트는 인터페이스만 쓰므로 구현체를 바꿔도 통과해야 한다.
통과하지 않는다면 추상화가 새고 있다는 뜻이다.

## 응답 형식을 바꿀 때

```
1. 응답 record 수정 (@JsonPropertyDescription 으로 각 필드의 의미를 적는다)
2. 프롬프트의 설명을 함께 고친다
3. validation 의 검증 규칙을 고친다
4. FakeAiAnalysisClient 의 기본 응답을 고친다
5. docs/api/analysis-api.md 의 스키마를 고친다
```

넷 중 하나만 고치면 어긋난다. 특히 프롬프트를 빠뜨리면 AI가 예전 형식으로 답한다.

## 모델을 바꿀 때

`ai.model` 설정값만 바꾼다. 코드에 모델 ID를 박지 않는다.

## 검증

```bash
./gradlew test --tests "*Analysis*"
```

이 패키지의 구현체는 실제 API를 부르므로 단위 테스트를 두지 않는다.
동작 확인은 키를 설정한 환경에서 수동으로 한다.
