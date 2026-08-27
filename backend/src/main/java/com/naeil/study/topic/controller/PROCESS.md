# topic/controller 작업 절차

## 엔드포인트를 추가할 때

```
1. 정말 필요한지 먼저 판단한다 (목록 응답으로 해결되는 경우가 많다)
2. 매핑과 DTO 변환만 작성한다
3. @WebMvcTest 로 슬라이스 테스트 작성
4. docs/api/analysis-api.md 갱신
```

## 검증 항목

```
GET /topics 분석 후      200 + 목록
GET /topics 분석 전      200 + 빈 배열
GET /topics 없는 세션    404
```

## 검증

```bash
./gradlew test --tests "*TopicControllerTest*"
```
