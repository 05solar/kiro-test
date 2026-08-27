# common 작업 절차

## 새 공통 요소를 추가할 때

```
1. 두 번째 사용처가 실제로 있는지 확인
2. 도메인 지식이 섞여 있지 않은지 확인
3. 추가 후 전체 테스트 실행 (공통 코드는 영향 범위가 넓다)
```

## 에러 코드를 추가할 때

```
1. common/exception/ErrorCode 에 항목 추가 (HTTP 상태 + 사용자 메시지)
2. 도메인 패키지의 exception 에 BusinessException 하위 클래스 생성
3. GlobalExceptionHandler 는 수정하지 않는다
4. docs/api/error-codes.md 표에 한 줄 추가
```

## 검증

```bash
./gradlew test
```

공통 코드 변경은 슬라이스 테스트만으로 부족하다. 전체 테스트를 돌린다.
