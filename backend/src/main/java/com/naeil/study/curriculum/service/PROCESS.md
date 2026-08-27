# curriculum/service 작업 절차

## 검증을 추가할 때

```
1. 기존 계획 확인보다 뒤에 둔다 (조회를 막지 않기 위해)
2. 전용 예외를 만들고 ErrorCode 를 붙인다
3. 서비스 테스트에 실패 케이스를 추가한다
```

## 테스트 작성 방식

리포지터리는 목으로, Planner는 실제 객체로 쓴다.
계획 규칙까지 목으로 만들면 서비스가 Planner 결과를 제대로 엔티티로 옮기는지 확인할 수 없다.

```java
curriculumService = new CurriculumService(
        curriculumRepository, studyStepRepository, topicRepository, sessionService,
        new CurriculumPlanner(5, 10, 45), fixedClock);
```

세션과 Topic의 id 는 JPA가 채우므로 리플렉션으로 넣는다.

## STEP 8 에서 한 일

예정대로 `StudyStepService` 를 따로 만들었다. `CurriculumService` 는 그대로 두었다.
계획을 세우는 일과 진행하는 일은 바뀌는 이유가 다르다.

검증 순서에서 "다른 진행 중 단계"를 순서보다 먼저 본다. STEP 1이 진행 중일 때
STEP 2를 시작하면 STEP 2는 첫 번째 `PENDING` 이라 순서 검사를 통과한다.
실제 원인은 동시 진행이므로 그 쪽을 먼저 알려주는 편이 맞다.

## 검증

```bash
./gradlew test --tests "*CurriculumServiceTest*" --tests "*StudyStepServiceTest*" \n  --tests "*CurriculumApiIntegrationTest*" --tests "*StudyStepApiIntegrationTest*"
```
