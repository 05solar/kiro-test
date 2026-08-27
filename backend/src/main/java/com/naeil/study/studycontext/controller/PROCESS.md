# studycontext/controller 작업 절차

## 엔드포인트를 추가할 때

```
1. 정말 필요한지 먼저 판단한다 (PUT 하나로 해결되는 경우가 많다)
2. 매핑과 DTO 변환만 작성한다
3. @WebMvcTest 로 슬라이스 테스트 작성
4. 통합 테스트에 실제 왕복 추가
5. docs/api/study-context-api.md 갱신
```

## 검증 항목

```
PUT 정상                 200
PUT 수정                 200
PUT 모든 값 null          200
PUT 2000자 초과           400 INVALID_REQUEST
PUT 정확히 2000자         200
PUT 없는 session          404
GET 존재                 200
GET 미입력               200 + 모든 값 null
```

## 검증

```bash
./gradlew test --tests "*StudyContextControllerTest*"
```
