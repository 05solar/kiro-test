# analysis/dto 작업 절차

## 필드를 추가할 때

```
1. 프론트가 실제로 쓰는 값인지 판단한다
2. record 에 필드 추가
3. AnalysisControllerTest 에 JSON 키 검증 추가
4. docs/api/analysis-api.md 예시 JSON 갱신
```

## 비동기로 옮길 때

`status` 가 `ANALYZING` 이 되고 `topicCount` 는 의미가 없어진다.
그때 이 record를 다시 설계한다.

## 검증

```bash
./gradlew test --tests "*AnalysisControllerTest*"
```
