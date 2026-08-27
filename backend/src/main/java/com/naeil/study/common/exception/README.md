# common/exception

에러 처리의 단일 경로.

| 클래스 | 역할 |
| --- | --- |
| `ErrorCode` | 코드 - HTTP 상태 - 사용자 메시지 매핑 |
| `BusinessException` | 모든 도메인 예외의 상위 타입. `ErrorCode` 를 갖는다 |
| `GlobalExceptionHandler` | `@RestControllerAdvice`. 예외를 `ErrorResponse` 로 변환 |

## 흐름

```
도메인 예외 발생 (SessionNotFoundException)
        ↓
BusinessException 으로 잡힌다
        ↓
ErrorCode 에서 HTTP 상태와 메시지를 꺼낸다
        ↓
ErrorResponse 로 응답
```

이 구조 덕분에 새 예외를 추가해도 `GlobalExceptionHandler` 는 고치지 않는다.

## 처리하지 못한 예외

`Exception` 핸들러가 받아 `INTERNAL_ERROR` 로 바꾸고, 원본은 `log.error` 로만 남긴다.
내부 메시지를 응답에 노출하지 않는다.
