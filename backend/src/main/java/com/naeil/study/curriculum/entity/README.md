# curriculum/entity

| 클래스 | 역할 |
| --- | --- |
| `Curriculum` | 학습 계획 |
| `StudyStep` | 계획의 한 단계 |
| `CurriculumStatus` | 계획 진행 상태 |
| `StudyStepType` | STUDY / REVIEW |
| `StudyStepStatus` | PENDING / IN_PROGRESS / COMPLETED / SKIPPED |
| `PriorityReason` | 계획에 포함된 이유 (화면 표시용) |

## Curriculum

```
id                       UUID
studySession             @OneToOne(LAZY), FK session_id (UNIQUE), 단방향
initialRemainingMinutes  계획 시점의 남은 시간. 이후 불변
totalAllocatedMinutes    배정 시간의 합
status                   CREATED → IN_PROGRESS → COMPLETED
```

## StudyStep

```
curriculum                @ManyToOne(LAZY)
topic                     @ManyToOne(LAZY), REVIEW 단계는 없음
stepOrder                 1부터
type / title / status
allocatedMinutes          이번 계획에서 배정한 시간
originalEstimatedMinutes  계획 시점 권장 시간 복사본. 이후 불변
mandatory                 사용자가 반드시 학습하겠다고 한 범위인지
priorityReasons           JSON 배열. 화면 표시용
startedAt / completedAt / actualStudyMinutes   학습을 진행할 때 채운다
```

생성은 `StudyStep.study(...)` 와 `StudyStep.review(...)` 로 나눠 둔다.
복습 단계는 Topic이 없고 권장 시간이라는 개념도 없어서 만드는 규칙이 다르다.

## Topic 값을 복사해 두는 이유

Topic은 재분석으로 통째로 교체된다. 계획을 세운 시점의 기준값이 남아 있어야
계획 대비 실제를 비교할 수 있다.

## JSON 컬럼

`priorityReasons` 는 `@JdbcTypeCode(SqlTypes.JSON)` 만 붙이고 `columnDefinition` 은
적지 않는다. PostgreSQL은 `jsonb`, H2는 `json` 으로 Dialect가 고른다.

## 상태 전이는 엔티티가 갖는다

```java
studyStep.start(now);       // 상태 검증 → startedAt → IN_PROGRESS
studyStep.complete(now);    // 시작 검증 → completedAt → 실제 학습시간 → COMPLETED
curriculum.startProgress(now);
curriculum.completeProgress(now);
session.startStep(order, now);
session.clearCurrentStep(now);
```

서비스에서 `setStatus` / `setStartedAt` 을 늘어놓지 않는다.
규칙이 여러 곳에 흩어지면 어느 것이 진짜 규칙인지 알 수 없게 된다.

`start` 와 `complete` 는 잘못된 상태에서 호출되면 예외를 던진다.
서비스가 앞서 걸러 내므로 실제로는 거의 걸리지 않는 마지막 방어선이다.

## 실제 학습시간은 올림한다

```
actualStudyMinutes = (경과초 + 59) / 60
```

`ChronoUnit.MINUTES` 로 버리면 40초 학습이 0분이 되고, 이후 재조정이
"시간을 전혀 쓰지 않았다"고 판단한다. 1초라도 지났으면 1분으로 센다.
시계가 뒤로 간 경우(음수)는 0으로 둔다.

배정 시간을 넘겨도 자르지 않는다. 초과분이 재조정의 입력이기 때문이다.
