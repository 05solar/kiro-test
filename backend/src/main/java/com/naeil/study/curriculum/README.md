# curriculum

남은 시간 안에서 실제로 수행 가능한 학습 계획.

```
curriculum
├── controller/   계획 생성 / 조회, 단계 시작 / 완료 API
├── service/      계획 생성 검증 + 학습 진행 상태 전이
├── planner/      시간 배분 알고리즘
├── repository/   Spring Data JPA
├── entity/       Curriculum, StudyStep + Enum 4종
├── dto/          응답 record
└── exception/    도메인 예외
```

## 이 단계가 하는 일

Topic을 그대로 나열하는 것이 아니다. 180분밖에 없는 사람에게 520분짜리 목록을 주면
계획이 아니라 목록이다. 무엇을 얼마나 공부할지 정하는 것이 여기서 할 일이다.

## AI를 부르지 않는다

중요도, 예상 시간, 학습 맥락 일치 여부는 이미 분석 단계에서 AI가 정했다.
남은 일은 그 값들로 시간을 나누는 것이고, 그건 규칙으로 하는 편이 낫다.

```
예측 가능하다      같은 입력이면 같은 결과가 나온다
검증 가능하다      시간 총합 같은 제약을 코드가 보장한다
비용이 들지 않는다  강의자료를 다시 보낼 이유가 없다
```

LLM에게 "180분에 맞춰 나눠 줘"라고 맡기면 합이 200분이 되거나, 반드시 넣어야 할 주제가
조용히 빠진다. 사용자는 그런 실수를 알아채기 어렵다.

## 시간 값 세 개

```
originalEstimatedMinutes  제대로 학습하는 데 필요하다고 본 시간 (계획 시점 복사본)
allocatedMinutes          이번 계획에서 배정한 시간
actualStudyMinutes        실제로 쓴 시간
```

예상 60분에 40분을 배정했고 실제로 52분이 걸렸다면 "계획보다 12분 초과"를 알 수 있다.
합치면 이 판단이 불가능해지고 동적 재조정의 근거도 사라진다.

## mustStudy는 제약이다

우선순위 가중치가 아니다. 중요도가 `LOW` 여도 계획에서 빼지 않는다.
`StudyStep.mandatory` 가 이 값이며, 이후 재조정에서도 같은 의미로 쓴다.

## 계획을 만든 것과 학습을 시작한 것은 다르다

계획을 만들어도 세션 상태는 `READY` 를 유지하고 `currentStepOrder` 는 비워 둔다.
`IN_PROGRESS` 로의 전환은 첫 단계를 시작할 때 한다.

## 계획을 만드는 일과 수행하는 일을 나눈다

```
CurriculumService  계획 생성 / 조회       세션당 한 번
StudyStepService   단계 시작 / 완료       단계 수만큼 반복
```

다루는 시간 값도 다르다. 한쪽은 배정 시간을 정하고, 다른 쪽은 실제 시간을 기록한다.
`CurriculumPlanner` 에 상태 변경 로직을 넣지 않는다.

## 상태 전이

```
StudyStep    PENDING → IN_PROGRESS → COMPLETED
Curriculum   CREATED → IN_PROGRESS → COMPLETED
Session      READY   → IN_PROGRESS
```

전이 규칙은 엔티티의 `start` / `complete` 메서드에 모아 둔다.
서비스가 필드를 하나씩 세팅하지 않는다.

시작할 수 있는 단계는 `PENDING` 중 가장 앞선 하나뿐이고,
한 계획에서 `IN_PROGRESS` 인 단계는 하나 이하다.

## 실제 학습시간은 서버가 계산한다

```
actualStudyMinutes = ceil((completedAt - startedAt) / 60초)
```

클라이언트가 보낸 값을 받지 않는다. 화면의 타이머는 표시용이다.
배정 시간을 넘겨도 자르지 않는다 — 초과분이 이후 재조정의 입력이기 때문이다.

## 남은 학습 시간을 차감하지 않는다

단계를 완료해도 `remainingStudyMinutes` 와 남은 단계의 `allocatedMinutes` 는 그대로다.
실제 학습시간에는 브라우저를 닫아 둔 시간까지 들어가므로, 그대로 빼면
시험까지 남은 실제 시간과 어긋난다. 다시 계산하는 것은 9단계의 일이다.

## 구현된 API

| 메서드 | 경로 | 상태 |
| --- | --- | --- |
| POST | `/api/sessions/{sessionCode}/curriculum` | 201(새로 생성) / 200(기존 반환) |
| GET | `/api/sessions/{sessionCode}/curriculum` | 200 / 404 |
| POST | `/api/sessions/{sessionCode}/steps/{stepId}/start` | 200 |
| POST | `/api/sessions/{sessionCode}/steps/{stepId}/complete` | 200 |

명세: `docs/api/curriculum-api.md`, `docs/api/study-step-api.md` (저장소 루트 기준)

진행 상태 조회는 계획 조회에 포함되어 있다. 별도 API를 만들지 않았다.

## 아직 없는 것

```
남은 시간 재계산            STEP 9
동적 재조정 (reallocate)    STEP 9
계획 재생성 API             정책 정리 후
일시정지 / 건너뛰기          MVP 제외
Quiz                       STEP 9
```
