# topic/dto 작업 절차

## 필드를 추가할 때

```
1. 화면에서 실제로 쓰는 값인지 판단한다 (내부 추적 정보는 넣지 않는다)
2. record 에 필드 추가 + from() 매핑
3. TopicControllerTest 에 JSON 키 검증 추가
4. docs/api/analysis-api.md 예시 JSON 갱신
```

## 검증

```bash
./gradlew test --tests "*TopicControllerTest*" --tests "*AnalysisApiIntegrationTest*"
```
