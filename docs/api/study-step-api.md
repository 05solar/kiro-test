# 학습 진행 API

만들어진 학습 계획을 실제로 수행한다. 단계를 시작하고 완료하며, 실제로 쓴 시간을 기록한다.

관련 문서: [curriculum-api.md](curriculum-api.md) · [error-codes.md](error-codes.md)

---

## 이 API가 다루는 것과 다루지 않는 것

| 다룬다 | 다루지 않는다 |
| --- | --- |
| 단계 시작 / 완료 | 남은 학습 시간 재계산 |
| 실제 학습시간 기록 | 남은 단계의 배정 시간 재조정 |
| 진행 중 단계 추적 | 일시정지 / 건너뛰기 |
| 계획 완료 판정 | 세션 최종 완료 판정 |

오른쪽 항목은 이후 단계에서 구현한다. 이 API는 **기록을 정확히 남기는 것**까지가 책임이다.

---

## 시간 값 네 가지

한 단계에는 성격이 다른 시간이 네 개 있고, 서로 덮어쓰지 않는다.

```
Topic.estimatedStudyMinutes        AI가 본 필요 학습시간          60분
StudyStep.originalEstimatedMinutes 계획 시점의 복사본 (불변)      60분
StudyStep.allocatedMinutes         이번 계획에서 배정한 시간       40분
StudyStep.actualStudyMinutes       사용자가 실제로 쓴 시간         52분
```

이 구분이 있어야 "40분 배정했는데 52분 걸렸다 → 12분 초과"를 판단할 수 있다.
하나로 합치면 이후 재조정의 근거가 사라진다.

---

## 상태 전이

```
PENDING ──start──> IN_PROGRESS ──complete──> COMPLETED
```

허용하지 않는 전이

| 요청 | 결과 |
| --- | --- |
| `PENDING` → complete | 409 `STUDY_STEP_NOT_STARTED` |
| `COMPLETED` → start | 409 `STUDY_STEP_ALREADY_COMPLETED` |
| `COMPLETED` → complete | **200** — 기존 결과를 그대로 반환 |
| `IN_PROGRESS` → start | **200** — 기존 상태를 그대로 반환 |

마지막 두 줄은 오류가 아니다. 버튼을 두 번 누르거나 요청이 재전송된 경우이며,
여기서 값을 다시 계산하면 시작 시각이 덮이거나 학습시간이 요청할 때마다 늘어난다.

`SKIPPED`는 열거형에 있지만 이 단계에서 만들지 않는다.

---

## POST /api/sessions/{sessionCode}/steps/{stepId}/start

학습을 시작한다. Request Body 없다.

### 검증 순서

```
세션 → 학습 계획 → 단계 소유 → 완료 여부 → (진행 중이면 그대로 반환)
  → 시험 시각 → 다른 진행 중 단계 → 순서
```

### 성공 — 200 OK

```json
{
  "stepId": "ca33d879-b300-446e-884f-3343027c8381",
  "stepOrder": 1,
  "type": "STUDY",
  "title": "프로세스와 스레드",
  "status": "IN_PROGRESS",
  "allocatedMinutes": 50,
  "actualStudyMinutes": null,
  "startedAt": "2026-08-27T20:46:56.405325",
  "completedAt": null
}
```

새로 시작한 경우와 이미 진행 중이던 경우의 응답이 같다. 201을 쓰지 않는 이유는
단계를 새로 만드는 것이 아니라 이미 있는 단계의 상태를 바꾸기 때문이다.

### 함께 바뀌는 것

| 대상 | 변화 |
| --- | --- |
| `StudySession.currentStepOrder` | 시작한 단계의 순번 |
| `StudySession.status` | `READY` → `IN_PROGRESS` (그 외 상태는 유지) |
| `Curriculum.status` | `CREATED` → `IN_PROGRESS` (그 외 상태는 유지) |
| `StudySession.lastAccessedAt` / `expiresAt` | 갱신 |

### 실패

| 상태 | 코드 | 상황 |
| --- | --- | --- |
| 400 | `INVALID_SESSION_CODE` | 세션 코드 형식 오류 |
| 400 | `INVALID_REQUEST` | `stepId`가 UUID 형식이 아님 |
| 404 | `SESSION_NOT_FOUND` | 없거나 만료된 세션 |
| 404 | `CURRICULUM_NOT_FOUND` | 아직 학습 계획을 만들지 않음 |
| 404 | `STUDY_STEP_NOT_FOUND` | 없거나 **다른 세션의 단계** |
| 409 | `STUDY_STEP_ALREADY_COMPLETED` | 이미 완료한 단계 |
| 409 | `EXAM_ALREADY_STARTED` | 시험 시각이 지남 |
| 409 | `ANOTHER_STEP_IN_PROGRESS` | 다른 단계가 진행 중 |
| 409 | `INVALID_STUDY_STEP_ORDER` | 앞선 단계를 건너뜀 |

---

## POST /api/sessions/{sessionCode}/steps/{stepId}/complete

학습을 마치고 실제 학습시간을 기록한다. Request Body 없다.

### 성공 — 200 OK

```json
{
  "completedStep": {
    "stepId": "ca33d879-b300-446e-884f-3343027c8381",
    "stepOrder": 1,
    "type": "STUDY",
    "title": "프로세스와 스레드",
    "status": "COMPLETED",
    "allocatedMinutes": 50,
    "actualStudyMinutes": 38,
    "startedAt": "2026-08-27T20:09:56.405325",
    "completedAt": "2026-08-27T20:47:06.239489"
  },
  "nextStep": {
    "stepId": "8cf3c63f-8395-4565-8513-d55548986c0f",
    "stepOrder": 2,
    "type": "STUDY",
    "title": "CPU 스케줄링",
    "status": "PENDING",
    "allocatedMinutes": 45,
    "actualStudyMinutes": null,
    "startedAt": null,
    "completedAt": null
  },
  "curriculumCompleted": false
}
```

`nextStep`은 **알려주기만 한다. 자동으로 시작하지 않는다.** 완료 버튼을 누른 시각과
실제로 다음 공부를 시작하는 시각은 다르고, 그 차이가 실제 학습시간에 그대로 들어가기 때문이다.

### 마지막 단계를 완료하면

```json
{
  "completedStep": { "...": "..." },
  "nextStep": null,
  "curriculumCompleted": true
}
```

`Curriculum.status`가 `COMPLETED`가 된다.
**`StudySession.status`는 `IN_PROGRESS`로 남는다.** 이후 퀴즈와 최종 복습이 남아 있기 때문이다.

### 함께 바뀌는 것

| 대상 | 변화 |
| --- | --- |
| `StudySession.currentStepOrder` | **`null`** — 진행 중인 단계가 없어졌다 |
| `Curriculum.status` | 남은 `PENDING`이 없으면 `COMPLETED` |
| `StudySession.remainingStudyMinutes` | **바뀌지 않는다** |
| 남은 단계의 `allocatedMinutes` | **바뀌지 않는다** |

### 실패

| 상태 | 코드 | 상황 |
| --- | --- | --- |
| 404 | `SESSION_NOT_FOUND` / `CURRICULUM_NOT_FOUND` / `STUDY_STEP_NOT_FOUND` | start와 동일 |
| 409 | `STUDY_STEP_NOT_STARTED` | 시작하지 않은 단계 |

시험 시각이 지나도 완료는 막지 않는다. 이미 학습한 기록이 존재하기 때문이다.

---

## 실제 학습시간 계산

```
actualStudyMinutes = ceil((completedAt - startedAt) / 60초)
```

정수 연산으로 `(경과초 + 59) / 60`을 쓴다.

`ChronoUnit.MINUTES`로 버리지 않는 이유는, 40초 공부한 단계가 0분으로 남으면
이후 재조정이 "시간을 전혀 쓰지 않았다"고 판단하기 때문이다. 1초라도 지났으면 1분으로 센다.

배정 시간을 넘겨도 **자르지 않는다.**

```
allocatedMinutes = 30
actualStudyMinutes = 62   ← 그대로 저장
```

30으로 잘라 저장하면 "계획보다 32분 초과"라는 사실이 사라진다.

### 클라이언트 값을 받지 않는다

```json
{ "actualStudyMinutes": 5 }   // 이런 요청 본문을 받지 않는다
```

화면의 타이머는 표시용이다. 기준은 서버가 기록한 `startedAt`과 완료 요청 시각이다.
클라이언트 값을 믿으면 조작할 수 있고, 여러 기기에서 접속했을 때 어느 값을 쓸지도 정할 수 없다.

### 브라우저를 닫아 둔 시간

시작 후 브라우저를 닫고 하루 뒤에 완료를 누르면 그 시간이 전부 실제 학습시간에 들어간다.
MVP에서는 이를 허용한다. 일시정지와 자리비움 감지는 이후 기능이다.

이 값을 그대로 남은 학습 시간에서 빼면 시험까지 남은 실제 시간과 어긋나므로,
**이 API는 남은 시간을 차감하지 않는다.**

---

## 학습 순서

```
STEP 1 COMPLETED
STEP 2 PENDING     ← 지금 시작할 수 있는 단계
STEP 3 PENDING     ← 시작하면 409 INVALID_STUDY_STEP_ORDER
```

시작할 수 있는 단계는 `PENDING` 중 순번이 가장 앞선 하나뿐이다.
계획이 중요도 순으로 배치되어 있어서, 뒤 단계부터 하면 시간이 모자랄 때 정작 중요한 단계가 남는다.

한 계획에서 `IN_PROGRESS`인 단계는 **항상 하나 이하**다.

```
노트북: STEP 2 시작      → 200
휴대폰: STEP 3 시작      → 409 ANOTHER_STEP_IN_PROGRESS
```

두 단계가 동시에 진행 중이면 같은 시간이 양쪽에 기록되어 재조정의 근거가 무너진다.

---

## 다른 세션의 단계

```
세션 A의 코드  +  세션 B의 stepId  →  404 STUDY_STEP_NOT_FOUND
```

403이 아니라 404다. 403을 주면 "그 단계는 존재한다"는 사실이 드러난다.
조회는 항상 `stepId`와 세션의 계획을 함께 확인한다.

---

## 재접속 복구

다른 기기에서 세션 코드만 입력해도 학습 상태가 복구되어야 한다.

```
GET /api/sessions/{sessionCode}            세션 상태, currentStepOrder
GET /api/sessions/{sessionCode}/curriculum 각 단계의 상태 / 시각 / 실제 학습시간
```

계획 조회 응답에 진행 정보가 모두 들어 있어 별도 API가 필요 없다.
자세한 응답 형식은 [curriculum-api.md](curriculum-api.md)를 참고한다.

```
STEP 1  COMPLETED    actualStudyMinutes 38
STEP 2  IN_PROGRESS  startedAt 있음        ← 이 화면을 연다
STEP 3  PENDING
```

`GET /steps/current` 같은 API는 만들지 않았다. 계획 조회로 충분하고,
같은 정보를 주는 API가 둘이면 어느 쪽이 맞는지 판단할 근거가 없어진다.

---

## 동시성

상태 검증과 트랜잭션으로 막는다. 단계 상태와 세션 / 계획 상태가 함께 바뀌므로
셋 다 성공하거나 셋 다 실패한다.

낙관적 잠금(`@Version`)은 넣지 않았다. 한 세션을 여러 기기에서 동시에 쓰는 것은
드문 경우이고, 그 경우에도 상태 검증이 이중 시작을 막는다.
같은 순간에 도착한 두 요청 중 하나가 어느 쪽이든, 결과는 항상 "진행 중인 단계 하나"다.
