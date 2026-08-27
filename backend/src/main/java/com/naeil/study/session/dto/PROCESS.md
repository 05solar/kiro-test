# session/dto 작업 절차

## 응답 필드를 추가할 때

```
1. record 에 필드 추가 (엔티티 필드 순서와 맞춘다)
2. from() 매핑 추가
3. SessionControllerTest 에 JSON 키 검증 추가
4. SessionApiIntegrationTest 에 실제 응답 확인 추가
5. docs/api/session-api.md 의 필드 표 + 예시 JSON 갱신
```

2번만 하고 3~5번을 빠뜨리면 응답이 조용히 바뀐다.

## 요청 DTO를 추가할 때 (STEP 3)

```
1. record 로 만든다
2. Bean Validation 애노테이션을 붙인다 (@NotBlank, @Future, @Positive)
3. 컨트롤러 파라미터에 @Valid 를 붙인다
4. 검증 실패 응답은 INVALID_REQUEST (400) 로 나간다
5. 실패 케이스 테스트를 작성한다
```

## 검증

```bash
./gradlew test --tests "*SessionControllerTest*" --tests "*SessionApiIntegrationTest*"
```
