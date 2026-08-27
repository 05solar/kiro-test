# document/exception 작업 절차

## 새 예외를 추가하는 순서

```
1. common/exception/ErrorCode 에 항목 추가 (HTTP 상태 + 한국어 메시지)
2. 여기에 BusinessException 하위 클래스 생성
3. 검증기 또는 서비스에서 throw
4. 테스트: 검증기 단위(예외 타입) + 컨트롤러(상태 코드, code 문자열)
5. docs/api/error-codes.md 와 docs/api/document-api.md 오류 표 갱신
```

## STEP 4-2 에서 추가할 것

```
DOCUMENT_PARSE_FAILED   텍스트 추출 실패
```

단, 파싱 실패는 요청 전체를 실패시키지 않는다.
문서 상태를 PARSE_FAILED 로 표시하고 나머지는 계속 진행한다.
따라서 예외보다 상태 값으로 다루는 쪽이 맞는지 먼저 판단한다.

## 검증

```bash
./gradlew test --tests "*DocumentFileValidatorTest*" --tests "*DocumentControllerTest*"
```
