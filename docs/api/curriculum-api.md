# 학습 계획 API 명세

분석된 Topic과 남은 학습 시간으로 실제 수행 가능한 학습 계획을 만든다.

**Topic을 그대로 나열하는 것이 아니다.** 180분밖에 없는 사람에게 520분짜리 목록을 주면
계획이 아니라 목록이다. 무엇을 얼마나 공부할지 정하는 것이 이 단계의 일이다.

- Base URL: `/api/sessions/{sessionCode}/curriculum`
- 인증: 없음. 8자리 세션 코드가 접근 키다.

## 목차

- [POST — 계획 생성](#post-apisessionssessioncodecurriculum--계획-생성)
- [GET — 계획 조회](#get-apisessionssessioncodecurriculum--계획-조회)
- [시간 값 세 개](#시간-값-세-개)
- [계획을 세우는 방식](#계획을-세우는-방식)
- [우선순위](#우선순위)
- [시간 압축 정책](#시간-압축-정책)
- [복습 단계](#복습-단계)
- [설정](#설정)

---

## POST /api/sessions/{sessionCode}/curriculum — 계획 생성

```http
POST /api/sessions/7K2M9QXF/curriculum
```

Request Body가 없다. 남은 학습 시간과 Topic은 이미 DB에 있다.

### 시작 조건

| 조건 | 실패 시 |
| --- | --- |
| 세션이 존재한다 | 404 `SESSION_NOT_FOUND` |
| 세션이 `READY` 상태다 (분석 완료) | 400 `SESSION_NOT_READY` |
| 분석된 Topic이 1개 이상 있다 | 400 `TOPICS_REQUIRED` |
| 남은 학습 시간이 0보다 크다 | 400 `NO_STUDY_TIME_AVAILABLE` |
| 최소 학습시간(5분) 이상 남았다 | 422 `CURRICULUM_GENERATION_FAILED` |

### 응답 — 201 Created / 200 OK

```json
{
  "curriculumId": "2f961e2b-85ec-405e-9c0e-88db27977732",
  "initialRemainingMinutes": 180,
  "totalAllocatedMinutes": 180,
  "status": "CREATED",
  "progress": {
    "completedSteps": 0,
    "totalSteps": 5,
    "percentage": 0
  },
  "steps": [
    {
      "id": "52daee4a-9bd2-49ba-87b1-855fa5c646eb",
      "order": 1,
      "type": "STUDY",
      "topicId": "b10bd0b7-e8c7-47d7-af20-9e3d421881e2",
      "title": "프로세스와 스레드",
      "importance": "VERY_HIGH",
      "originalEstimatedMinutes": 50,
      "allocatedMinutes": 50,
      "actualStudyMinutes": null,
      "mandatory": false,
      "priorityReasons": ["CORE_TOPIC"],
      "status": "PENDING",
      "startedAt": null,
      "completedAt": null
    },
    {
      "id": "3515a653-7fec-4902-a16c-a4d875b5f56b",
      "order": 3,
      "type": "STUDY",
      "topicId": "57cc6697-818d-4593-acb0-81ecae907dd3",
      "title": "가상 메모리",
      "importance": "HIGH",
      "originalEstimatedMinutes": 55,
      "allocatedMinutes": 43,
      "actualStudyMinutes": null,
      "mandatory": false,
      "priorityReasons": ["CORE_TOPIC", "WEAK_AREA"],
      "status": "PENDING",
      "startedAt": null,
      "completedAt": null
    }
  ]
}
```

### 201과 200을 구분한다

| 상태 | 의미 |
| --- | --- |
| 201 | 이번 요청으로 계획을 새로 만들었다 |
| 200 | 이미 있던 계획을 그대로 돌려줬다 |

**이미 계획이 있으면 다시 만들지 않는다.** 같은 요청을 두 번 보내도 결과가 같아야 하고,
사용자가 이미 본 계획이 새로고침 한 번에 바뀌면 안 된다.
만들지 않았는데 201을 돌려주면 클라이언트가 생성 여부를 구분할 수 없다.

계획을 다시 세우는 기능은 이 단계에서 만들지 않는다.

### 부수 효과

```
lastAccessedAt = now
expiresAt      = now + 30일
```

**세션 상태는 `READY`를 유지한다.** 계획을 만든 것과 학습을 시작한 것은 다르다.
`IN_PROGRESS`로의 전환과 `currentStepOrder` 설정은 첫 단계를 시작할 때 한다.

시험까지 남은 실제 시간이 저장된 값보다 짧으면 세션의 `remainingStudyMinutes`도 함께 낮춘다.

---

## GET /api/sessions/{sessionCode}/curriculum — 계획 조회

```http
GET /api/sessions/7K2M9QXF/curriculum
```

응답 형식은 생성과 같다. 단계는 `order` 오름차순이다.

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_SESSION_CODE` | 세션 코드 형식 오류 |
| 404 | `SESSION_NOT_FOUND` | 세션 없음 |
| 404 | `CURRICULUM_NOT_FOUND` | 아직 계획을 만들지 않음 |

### 진행 상태를 함께 담는다

학습이 진행되면 각 단계에 진행 정보가 채워진다. 동적 재조정(9단계)으로 줄어든 배정 시간이나
시간 부족으로 제외된 단계도 이 응답에 그대로 반영된다.

```json
{
  "progress": { "completedSteps": 1, "skippedSteps": 1, "totalSteps": 3, "percentage": 67 },
  "steps": [
    { "order": 1, "status": "COMPLETED",   "allocatedMinutes": 50, "actualStudyMinutes": 65,
      "skipReason": null, "startedAt": "2026-08-27T20:09:56", "completedAt": "2026-08-27T21:14:06" },
    { "order": 2, "status": "PENDING",     "allocatedMinutes": 30, "actualStudyMinutes": null,
      "skipReason": null, "startedAt": null, "completedAt": null },
    { "order": 3, "status": "SKIPPED",     "allocatedMinutes": 0,  "actualStudyMinutes": null,
      "skipReason": "TIME_CONSTRAINT", "startedAt": null, "completedAt": null }
  ]
}
```

다른 기기에서 세션 코드만 입력해도 이 응답 하나로 학습 화면을 복구할 수 있다.
그래서 "현재 단계 조회" API를 따로 만들지 않았다. 같은 정보를 주는 API가 둘이면
어느 쪽이 맞는지 판단할 근거가 없어진다.

`progress`는 DB에 저장하지 않고 단계 상태에서 매번 센다. 복습 단계도 전체에 포함하며,
`percentage`는 반올림한 정수다. 진행률을 따로 저장하면 단계 상태와 어긋날 수 있다.

**`SKIPPED`도 처리된 단계로 센다.** 시간 부족으로 제외된 단계는 더 이상 수행 대상이 아니므로
진행 흐름상 완료한 것과 같이 지나간 단계다. 그래서 진행률은 `(완료 + 제외) / 전체`이며,
화면에서 둘을 구분할 수 있도록 `completedSteps`와 `skippedSteps`를 나눠 담는다.
위 예는 완료 1 + 제외 1 = 처리 2 / 전체 3 → 67%다.

단계를 시작·완료하는 방법과 동적 재조정 규칙은 [study-step-api.md](study-step-api.md)를 참고한다.

---

## 시간 값 세 개

이 구분이 이후 동적 재조정의 근거가 된다.

| 값 | 의미 | 언제 정해지나 |
| --- | --- | --- |
| `originalEstimatedMinutes` | 이 주제를 제대로 학습하는 데 필요한 시간 | 분석 단계 (AI 판단) |
| `allocatedMinutes` | 이번 계획에서 실제 배정한 시간 | 계획 단계 |
| `actualStudyMinutes` | 사용자가 실제로 쓴 시간 | 학습 진행 단계 (8단계) |

```
예상 60분 → 시간이 부족해 40분 배정 → 실제로 52분 사용
                                      → 계획보다 12분 초과
```

세 값을 하나로 합치면 이 판단이 불가능해진다.

`Curriculum`에도 두 값을 나눠 둔다.

| 값 | 의미 |
| --- | --- |
| `initialRemainingMinutes` | 계획을 세운 시점의 남은 시간. 이후 불변 |
| `totalAllocatedMinutes` | 배정 시간의 합. 항상 위 값 이하 |

---

## 계획을 세우는 방식

**규칙 기반이다. AI를 부르지 않는다.**

중요도, 예상 시간, 학습 맥락 일치 여부는 이미 분석 단계에서 AI가 정했다.
남은 일은 그 값들로 시간을 나누는 것이고, 그건 규칙으로 하는 편이 낫다.

```
예측 가능하다      같은 입력이면 같은 결과가 나온다
검증 가능하다      시간 총합 같은 제약을 코드가 보장한다
비용이 들지 않는다  강의자료를 다시 보낼 이유가 없다
```

LLM에게 "180분에 맞춰 나눠 줘"라고 맡기면 합이 200분이 되거나, 반드시 넣어야 할 주제가
조용히 빠진다. 사용자는 그런 실수를 알아채기 어렵다.

### 세 단계

```
1. 선택   우선순위 순으로, 최소 시간을 확보할 수 있는 주제만 담는다
2. 배분   남은 시간을 우선순위 순으로 나눠 권장 시간까지 늘린다
3. 정렬   원래 학습 순서로 되돌리고, 시간이 남으면 복습 단계를 붙인다
```

큰 주제가 안 들어가도 거기서 멈추지 않는다. 뒤의 짧은 주제는 들어갈 수 있다.

### 최종 순서는 원래 학습 순서를 따른다

중요하다고 뒤 내용을 앞으로 끌어오면 선수 개념 없이 공부하게 된다.

```
프로세스 개념 → CPU 스케줄링
```

이 관계인데 CPU 스케줄링이 더 중요하다고 먼저 보여주면 학습 흐름이 깨진다.
중요도는 "무엇을 넣을지"에만 쓰고, "어떤 순서로 볼지"는 `topicOrder`를 지킨다.

---

## 우선순위

숫자 점수를 매기지 않는다. `VERY_HIGH = 100, 교수 강조 = +30` 같은 계수는 근거 없이
정해지고, 한 번 박히면 왜 그 값인지 아무도 설명하지 못한다.
무엇이 무엇보다 앞서는지만 순서로 정한다.

```
1. mustStudyMatched          사용자가 반드시 하겠다고 한 것
2. importance                학습 우선순위
3. professorEmphasisMatched  교수님 강조
4. pastExamMatched           기출/예상
5. weakAreaMatched           자신 없는 부분
6. topicOrder                원래 학습 순서
```

**이 순서를 선택과 배분에 모두 쓴다.** 그래서 자신 없다고 표시한 주제는 같은 중요도의
다른 주제보다 시간이 덜 깎인다. 별도 보정 규칙을 두지 않아도 그렇게 된다.

### mustStudy는 제약이다

우선순위 가중치가 아니다. 중요도가 `LOW`여도 계획에서 빼지 않는다.

```
CPU 스케줄링  VERY_HIGH  60분
교착상태      LOW        30분  mustStudy

남은 시간 60분  →  교착상태가 살아남는다
```

응답의 `mandatory` 가 이 값이다.

### priorityReasons

왜 이 단계가 계획에 들어갔는지 보여주는 값이다. 계산에 쓰지 않는다.

```
CORE_TOPIC          중요도가 VERY_HIGH 또는 HIGH
PROFESSOR_EMPHASIS  교수님 강조
PAST_EXAM           기출/예상 문제
WEAK_AREA           자신 없는 부분
MUST_STUDY          반드시 공부할 범위
```

화면에서 "기출 관련", "취약 영역" 처럼 보여줄 수 있다.

---

## 시간 압축 정책

시간이 부족하다고 주제를 지우기만 하지 않는다. 60분짜리를 30분 핵심 학습으로 줄일 수 있다.

다만 무한정 줄이지는 않는다. 중요도별로 하한을 둔다.

| 중요도 | 하한 |
| --- | --- |
| `VERY_HIGH` | 권장 시간의 60% |
| `HIGH` | 50% |
| `MEDIUM` | 30% |
| `LOW` | 최소 학습시간(5분) |

10분짜리로 쪼갠 여러 주제보다 제대로 본 몇 개가 낫다는 판단이다.
하한을 확보할 수 없는 주제는 계획에 넣지 않는다.

> 이 비율은 **MVP 휴리스틱**이지 학습 과학의 기준이 아니다.
> 실제 사용 결과를 보고 고칠 값이라 `CurriculumPolicy` 한 곳에 모아 두었다.

### 배정 시간은 권장 시간을 넘지 않는다

최초 계획에서 권장 시간보다 더 주지 않는다. 그 시간을 다른 주제에 쓰는 편이 낫고,
여유가 있으면 복습 단계로 간다.

---

## 복습 단계

모든 주제를 권장 시간대로 배정하고도 시간이 남으면 마지막에 복습 단계를 만든다.

```
type    REVIEW
topicId null
title   핵심 개념 최종 복습
```

| 조건 | 값 |
| --- | --- |
| 만드는 기준 | 남는 시간 10분 이상 |
| 최대 시간 | 45분 |

남는 시간이 1~9분이면 만들지 않는다. 그런 단계는 계획을 어수선하게 만들 뿐이다.
45분을 넘게 남아도 전부 복습에 몰아주지 않는다. 남는 시간을 억지로 다 쓸 이유가 없다.

---

## 서버가 보장하는 것

계획이 나가기 전에 다음을 확인한다. 규칙으로 만들었으니 깨질 리 없다고 두지 않는다.

```
배정 시간의 합 <= 가용 시간
각 단계의 배정 시간 >= 최소 학습시간
각 단계의 배정 시간 <= 권장 시간
같은 주제가 두 번 들어가지 않는다
존재하는 주제만 쓴다
단계 번호가 1부터 빠짐없이 이어진다
```

하나라도 어긋나면 `CURRICULUM_GENERATION_FAILED`로 실패시킨다.
실행 불가능한 일정을 조용히 내보내지 않는다.

---

## 시험까지 남은 시간 재확인

계획을 세우기 직전에 현재 시각 기준으로 다시 계산한다.

```
effectiveMinutes = min(세션의 남은 학습 시간, 지금부터 시험까지 남은 분)
```

시험 정보를 입력한 뒤 시간이 흘렀을 수 있다. 저장된 값이 300분이어도 시험까지
180분밖에 안 남았다면 300분짜리 계획은 실행할 수 없다.

더 짧아진 경우 세션의 `remainingStudyMinutes`도 함께 낮춘다.
이후 단계들이 같은 기준을 보게 하기 위해서다.

### 안전 여유시간을 따로 빼지 않는다

사용자가 입력한 `availableStudyMinutes` 자체가 이미 "실제로 공부할 수 있는 시간"이다.
서버가 임의로 20분씩 떼어내면 사용자의 판단을 두 번 적용하는 셈이 된다.

---

## 설정

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `CURRICULUM_MIN_TOPIC_MINUTES` | `5` | 단계 하나에 배정하는 최소 시간 |
| `CURRICULUM_REVIEW_MIN_MINUTES` | `10` | 복습 단계를 만드는 최소 잔여 시간 |
| `CURRICULUM_REVIEW_MAX_MINUTES` | `45` | 복습 단계 최대 시간 |

---

## 상태 값

### CurriculumStatus

```
CREATED → IN_PROGRESS → COMPLETED
```

계획을 만든 직후에는 `CREATED`다. 첫 단계를 시작하면 `IN_PROGRESS`,
남은 `PENDING` 단계가 없어지면 `COMPLETED`가 된다.
계획이 끝나도 세션은 `IN_PROGRESS`로 남는다 — 이후 퀴즈와 최종 복습이 있기 때문이다.

### StudyStepStatus

```
PENDING → IN_PROGRESS → COMPLETED
   │
   └──(다른 단계 완료 시 남은 시간 부족)──> SKIPPED
```

계획을 만든 직후에는 모두 `PENDING`이며 `startedAt` / `completedAt` / `actualStudyMinutes`가 비어 있다.
상태를 바꾸는 방법은 [study-step-api.md](study-step-api.md)를 참고한다.
`SKIPPED`는 9단계 동적 재조정에서 남은 시간이 부족해 자동으로 제외된 단계다.
`allocatedMinutes`가 0이 되고 `skipReason`이 `TIME_CONSTRAINT`로 채워진다. 사용자가 직접 건너뛴
것이 아니다.
