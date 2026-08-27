# common/dto 작업 절차

## 응답 형식을 바꿀 때

에러 응답 형식 변경은 모든 클라이언트에 영향을 준다.

```
1. 변경이 정말 필요한지 확인한다
2. ErrorResponse 수정
3. GlobalExceptionHandler 확인
4. 전체 테스트 실행 (에러 응답을 검증하는 테스트가 여러 곳에 있다)
5. docs/api/error-codes.md 갱신
```

## 새 공통 DTO를 추가할 때

성공 응답까지 공통 래퍼(`{ "data": ..., "success": true }`)로 감싸지 않는다.
HTTP 상태 코드가 이미 성공/실패를 표현한다. 래퍼는 클라이언트 코드만 늘린다.
