# curriculum 작업 절차

## 계획 규칙을 바꿀 때

```
1. planner/CurriculumPolicy 의 상수나 Comparator 를 고친다
2. CurriculumPlannerTest 에 결정적인 케이스를 추가한다
   (무작위 입력 테스트는 제약만 검증한다. 정책은 명시적 케이스로 확인한다)
3. docs/api/curriculum-api.md 의 정책 표를 갱신한다
```

정책 숫자를 서비스나 컨트롤러에 흩어 놓지 않는다.

## 필드를 추가할 때

```
1. 엔티티에 필드 + Javadoc (누가 채우고 언제 바뀌는지)
2. 정적 팩터리 시그니처에 반영
3. planner 의 PlannedStep 에도 필요한지 판단
4. 응답 DTO 에 노출할지 판단
5. CurriculumRepositoryTest 에 영속화 확인 추가
6. 실제 PostgreSQL 로 왕복 확인 (특히 JSON 컬럼)
7. docs/database.md 갱신
```

## STEP 8 (학습 진행) 에서 할 일

```
StudyStep.start(now)     PENDING → IN_PROGRESS, startedAt 기록
StudyStep.complete(now)  IN_PROGRESS → COMPLETED, completedAt + actualStudyMinutes 기록
Curriculum.status        CREATED → IN_PROGRESS → COMPLETED
StudySession             READY → IN_PROGRESS, currentStepOrder 갱신
```

`originalEstimatedMinutes` 와 `actualStudyMinutes` 는 한 번 정해지면 바꾸지 않는다.

## STEP 9 (동적 재조정) 에서 할 일

`CurriculumPlanner` 에 `reallocate(...)` 를 더한다. 지금 만들지 않는다.

```
바꿀 수 있는 것    PENDING 단계의 allocatedMinutes
바꾸지 않는 것     COMPLETED 단계, originalEstimatedMinutes, actualStudyMinutes
```

`PlanningTopic` / `PlannedStep` 이 JPA 엔티티에 묶여 있지 않으므로 같은 입력 형태를
재조정에서도 쓸 수 있다.

## 검증

```bash
./gradlew test --tests "*Curriculum*"
./gradlew build
```
