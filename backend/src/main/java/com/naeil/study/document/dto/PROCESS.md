# document/dto 작업 절차

## 응답 필드를 추가할 때

```
1. 사용자에게 필요한 값인지 먼저 판단한다
   (저장 경로 계열은 추가하지 않는다)
2. record 에 필드 추가 + from() 매핑
3. DocumentControllerTest 에 JSON 키 검증 추가
4. DocumentApiIntegrationTest 에 실제 응답 확인 추가
5. docs/api/document-api.md 예시 JSON 갱신
```

## STEP 4-2 에서

extractedText 는 응답에 넣지 않는다. 본문 전체를 API로 내보낼 이유가 없다.
필요하면 길이나 요약만 노출한다.

## 검증

```bash
./gradlew test --tests "*DocumentControllerTest*" --tests "*DocumentApiIntegrationTest*"
```
