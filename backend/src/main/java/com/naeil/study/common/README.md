# common

둘 이상의 도메인이 함께 쓰는 코드만 둔다.

```
common
├── config/      공통 빈 (Clock)
├── dto/         ErrorResponse
└── exception/   ErrorCode, BusinessException, GlobalExceptionHandler
```

## 여기에 두는 것

- 모든 API가 같은 형식으로 응답해야 하는 것 (에러 응답)
- 모든 도메인이 같은 방식으로 다뤄야 하는 것 (시간)

## 여기에 두지 않는 것

- 특정 도메인의 규칙 (세션 코드 형식 → `session/entity`)
- 아직 한 곳에서만 쓰는 유틸리티
