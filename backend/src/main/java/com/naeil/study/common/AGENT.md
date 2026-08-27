# common — 에이전트 작업 규칙

## 지킬 것

- 도메인 지식을 넣지 않는다. `common` 은 어떤 도메인도 몰라야 한다.
- 에러 처리 경로는 하나로 유지한다. `BusinessException` + `ErrorCode` 조합을 벗어나지 않는다.
- 시간은 `Clock` 빈으로만 다룬다.

## 하지 말 것

- `common` 에서 도메인 패키지의 클래스를 import
- `GlobalExceptionHandler` 에 도메인별 `@ExceptionHandler` 추가
  (도메인 예외는 `BusinessException` 하나로 처리된다)
- 예외 원문 메시지를 응답에 노출
