# curriculum/entity 작업 절차

## 필드를 추가할 때

```
1. 필드 + @Column(name = "snake_case")
2. Javadoc 에 누가 채우고 언제 바뀌는지 적는다
3. 정적 팩터리 시그니처에 반영
4. CurriculumRepositoryTest 에 영속화 확인 추가
5. 실제 PostgreSQL 로 왕복 확인
6. docs/database.md 갱신
```

## STEP 8 에서 추가할 상태 전이 메서드

```java
void start(LocalDateTime now)                      PENDING → IN_PROGRESS
void complete(LocalDateTime now, int actualMinutes) IN_PROGRESS → COMPLETED
void skip(LocalDateTime now)                        PENDING → SKIPPED
```

잘못된 전이는 예외로 막는다. `startParsing` 이나 `updateExamInfo` 와 같은 방식이다.

## STEP 9 에서 바꿀 수 있는 값

```
바꿀 수 있는 것   PENDING 단계의 allocatedMinutes
바꾸지 않는 것    COMPLETED 단계, originalEstimatedMinutes, actualStudyMinutes
```

`originalEstimatedMinutes` 에 `updatable = false` 를 걸어 둔 이유다.

## STEP 8 에서 한 일

상태 전이 메서드를 엔티티에 넣었다.

```
StudyStep.start(now) / complete(now)
Curriculum.startProgress(now) / completeProgress(now)
StudySession.startStep(order, now) / clearCurrentStep(now) / isExamStarted(now)
```

`complete` 안에서 실제 학습시간을 올림으로 계산한다. 버리면 40초 학습이 0분이 된다.

컬럼은 7단계에서 이미 만들어 둔 `started_at` / `completed_at` / `actual_study_minutes` 를
그대로 쓴다. 스키마 변경이 없었다.

## 검증

```bash
./gradlew test --tests "*CurriculumRepositoryTest*" --tests "*StudyStepServiceTest*"
```
