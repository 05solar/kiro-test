# common/exception — 에이전트 작업 규칙

## 지킬 것

- 새 예외는 반드시 `BusinessException` 을 상속한다.
- HTTP 상태는 `ErrorCode` 에만 존재한다. 컨트롤러/서비스에서 상태 코드를 다루지 않는다.
- 세션 관련 에러는 존재 여부를 노출하지 않는다.
  없는 세션과 만료된 세션은 같은 `SESSION_NOT_FOUND` 로 응답한다.
- 로그 레벨: 비즈니스 예외는 `info`, 처리하지 못한 예외는 `error`.

## 하지 말 것

- `GlobalExceptionHandler` 에 도메인별 핸들러 추가
- 예외 메시지에 내부 정보(UUID, 생성 시각, 쿼리) 포함
- `@ResponseStatus` 로 상태 코드를 예외 클래스에 직접 지정
  (`ErrorCode` 와 이중 관리가 된다)
- `RuntimeException` 을 그대로 throw
