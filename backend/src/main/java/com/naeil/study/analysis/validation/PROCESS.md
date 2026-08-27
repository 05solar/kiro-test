# analysis/validation 작업 절차

## 규칙을 추가할 때

```
1. 실패인지 보정인지 먼저 정한다
   실패  구조가 깨져 저장할 수 없다
   보정  값이 조금 벗어났을 뿐 쓸 수 있다
2. 보정은 반드시 로그를 남긴다 (조용히 고치면 AI 품질 저하를 눈치채지 못한다)
3. AiTopicResponseValidatorTest 에 경계값 테스트를 추가한다
4. docs/api/analysis-api.md 갱신
```

## 판단 기준

전체 실패는 재분석을 뜻한다. AI 호출 비용과 사용자 대기 시간이 다시 든다.
"이 값이 잘못되면 Topic을 저장할 수 없는가"를 기준으로 정한다.
저장은 되는데 조금 이상한 정도면 보정한다.

## 검증

```bash
./gradlew test --tests "*AiTopicResponseValidatorTest*"
```
