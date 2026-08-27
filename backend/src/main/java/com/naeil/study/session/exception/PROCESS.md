# session/exception 작업 절차

## 새 예외를 추가하는 순서

```
1. common/exception/ErrorCode 에 항목 추가 (HTTP 상태 + 한국어 메시지)
2. 여기에 BusinessException 하위 클래스 생성
3. 서비스에서 throw
4. 테스트: 서비스 단위(예외 타입) + 컨트롤러(상태 코드, code 문자열)
5. docs/api/error-codes.md 표에 한 줄 추가
```

GlobalExceptionHandler 는 고치지 않는다.

## STEP 3 에서 추가한 예외

```
INVALID_EXAM_TIME   시험 시간이 현재보다 과거 (구현 완료)
학습 가능 시간 범위는 Bean Validation 으로 처리한다 (구현 완료)
```

## 검증

```bash
./gradlew test --tests "*SessionServiceTest*" --tests "*SessionControllerTest*"
```
