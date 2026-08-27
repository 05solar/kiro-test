# common/exception 작업 절차

## 새 에러를 추가하는 순서

```
1. ErrorCode 에 항목 추가
   - HTTP 상태
   - 사용자에게 보여줄 한국어 메시지
2. 도메인 패키지의 exception 에 BusinessException 하위 클래스 생성
3. 서비스에서 throw
4. 테스트 작성 (상태 코드 + code 문자열 검증)
5. docs/api/error-codes.md 표에 한 줄 추가
```

`GlobalExceptionHandler` 는 3번에서도 4번에서도 고치지 않는다.
고쳐야 한다면 설계가 어긋난 것이다.

## 검증

```bash
./gradlew test --tests "*ControllerTest*"
```
