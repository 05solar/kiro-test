# 정리 API

푼 문제를 되돌아보고, 틀린 것만 다시 풀고, 공부를 마친 뒤 전체를 한 장으로 훑어본다.

- Base URL: `/api/sessions/{sessionCode}`
- 인증: 없음. 8자리 세션 코드가 접근 키다.

**AI를 부르지 않는다.** 두 엔드포인트 모두 이미 저장된 것만 모아 준다 —
요약은 분석 단계에서 만들어 `topics.summary` 에 넣어 둔 값이고, 문제와 답안은
채점 때 저장한 값이다. 정리 화면을 몇 번 열어도 과금되지 않는다.

관련 문서: [quiz-api.md](quiz-api.md) · [curriculum-api.md](curriculum-api.md) · [error-codes.md](error-codes.md)

## 목차

- [GET /topics/{topicId}/quiz-review — STEP 풀이 내역](#get-topicstopicidquiz-review--step-풀이-내역)
- [GET /review — 세션 전체 정리](#get-review--세션-전체-정리)
- [안 푼 문제의 정답을 감춘다](#안-푼-문제의-정답을-감춘다)
- [마지막 회차만 본다](#마지막-회차만-본다)
- [틀린 문제 다시 풀기는 기록되지 않는다](#틀린-문제-다시-풀기는-기록되지-않는다)

---

## GET /topics/{topicId}/quiz-review — STEP 풀이 내역

```http
GET /api/sessions/7K2M9QXF/topics/{topicId}/quiz-review
```

한 STEP(Topic)에서 푼 문제를 **문제 본문·내 답·정답·해설까지** 함께 돌려준다.

### `/quiz-results` 와 무엇이 다른가

| | `/quiz-results` | `/quiz-review` |
| --- | --- | --- |
| 담는 것 | 점수 집계(숫자) | 문제 본문·보기·정답·해설 |
| 쓰는 화면 | 퀴즈 결과, 학습 화면의 내역 버튼 노출 판단 | 퀴즈 내역 화면 |
| 응답 크기 | 작다 | 문제 수에 비례해 커진다 |

나눠 둔 이유는 점수만 필요한 화면이 문제 전문까지 받아 갈 이유가 없기 때문이다.

### 조건

| 조건 | 실패 시 |
| --- | --- |
| 세션이 존재한다 | 404 `SESSION_NOT_FOUND` |
| Topic이 이 세션 소유다 | 404 `TOPIC_NOT_FOUND` |
| 해당 Topic에 퀴즈가 있다 | 404 `QUIZ_NOT_FOUND` |

### 응답 — 200 OK

```json
{
  "topicId": "cbf18698-8e1f-4399-b7d4-f7050bc7c57e",
  "topicTitle": "요구 사항 엔지니어링 개요",
  "round": 1,
  "totalQuestions": 4,
  "answeredQuestions": 4,
  "wrongQuestions": 1,
  "items": [
    {
      "quizId": "01cb2e8a-ed10-4e48-b848-259c452bc519",
      "quizOrder": 1,
      "question": "소프트웨어 엔지니어가 요구 사항 도출 과정에서 협력하는 대상은?",
      "options": ["시스템 이해관계자", "소프트웨어 테스터", "하드웨어 제조업체", "재무 관리자"],
      "difficulty": "EASY",
      "answered": true,
      "selectedIndex": 1,
      "correct": false,
      "correctIndex": 0,
      "explanation": "소프트웨어 엔지니어는 다양한 시스템 이해관계자와 협력하여 …",
      "answeredAt": "2026-08-28T16:19:37.373446"
    },
    {
      "quizId": "77d15bd6-1ceb-401a-869f-1cfa6f70ea68",
      "quizOrder": 2,
      "question": "요구 사항 명세의 목적으로 옳은 것은?",
      "options": ["…", "…", "…", "…"],
      "difficulty": "MEDIUM",
      "answered": false,
      "selectedIndex": null,
      "correct": false,
      "correctIndex": null,
      "explanation": null,
      "answeredAt": null
    }
  ]
}
```

| 필드 | 의미 |
| --- | --- |
| `round` | 마지막 회차. "새로운 퀴즈"를 만들면 올라간다 |
| `totalQuestions` | 마지막 회차의 문제 수 |
| `answeredQuestions` | 그중 답한 문제 수 |
| `wrongQuestions` | 답했고 틀린 문제 수. **안 푼 문제는 세지 않는다** |
| `items[].answered` | 이 문제에 답했는가 |
| `items[].correctIndex` | 정답 보기(0~3). **답한 문제에만** 들어 있다 |
| `items[].explanation` | 해설. **답한 문제에만** 들어 있다 |

---

## GET /review — 세션 전체 정리

```http
GET /api/sessions/7K2M9QXF/review
```

세션의 STEP 전부와 각 STEP의 요약·문제·답안을 **한 번에** 돌려준다.

화면 세 갈래(STEP별 요약 / 푼 문제 전체 / 틀린 문제만)가 이 응답 하나를 나눠 쓴다.
틀린 문제만 보는 화면도 따로 부르지 않고 `correct` 가 `false` 인 것을 골라 쓴다 —
같은 자료를 세 번 받아 갈 이유가 없다.

### 조건

| 조건 | 실패 시 |
| --- | --- |
| 세션이 존재한다 | 404 `SESSION_NOT_FOUND` |
| 학습 계획이 있다 | 404 `CURRICULUM_NOT_FOUND` |

**완료 여부로 막지 않는다.** 진행 중이어도 지금까지의 정리는 볼 수 있다.
정리 기능을 언제 열지는 `completed` 를 보고 화면이 정한다.

### 응답 — 200 OK

```json
{
  "sessionCode": "7K2M9QXF",
  "subject": "소프트웨어공학",
  "examScope": "3~5장",
  "examAt": "2026-08-29T09:00:00",
  "completed": true,
  "totalSteps": 5,
  "completedSteps": 4,
  "totalQuestions": 20,
  "answeredQuestions": 18,
  "correctAnswers": 14,
  "wrongAnswers": 4,
  "scorePercentage": 78,
  "steps": [
    {
      "stepId": "c5fdad46-2719-4779-96dc-a37b74239743",
      "stepOrder": 1,
      "title": "요구 사항 엔지니어링 개요",
      "type": "STUDY",
      "status": "COMPLETED",
      "allocatedMinutes": 40,
      "actualStudyMinutes": 37,
      "topicId": "cbf18698-8e1f-4399-b7d4-f7050bc7c57e",
      "topicTitle": "요구 사항 엔지니어링 개요",
      "summary": "요구 사항 엔지니어링은 …",
      "keyPoints": ["이해관계자 식별", "기능/비기능 구분"],
      "importance": "HIGH",
      "round": 1,
      "totalQuestions": 4,
      "answeredQuestions": 4,
      "wrongQuestions": 1,
      "quizzes": [ /* quiz-review 의 items 와 같은 형식 */ ]
    }
  ]
}
```

| 필드 | 의미 |
| --- | --- |
| `completed` | 모든 STEP이 정리됐는가. **`SKIPPED` 는 남은 것으로 세지 않는다** |
| `completedSteps` | `COMPLETED` 인 STEP 수. `SKIPPED` 는 빠진다 |
| `scorePercentage` | **푼 문제 기준** 정답률. 안 푼 문제는 분모에서 뺀다 |
| `steps[].topicId` | 휴식·복습처럼 Topic이 없는 STEP이면 `null` |
| `steps[].summary` | 분석 단계에서 만들어 둔 요약. 여기서 새로 만들지 않는다 |

### `completed` 판정

시간이 모자라 잘라 낸(`SKIPPED`) STEP까지 끝내야 완료라면 영영 완료가 되지 않는다.
그래서 `PENDING` 이나 `IN_PROGRESS` 인 STEP이 하나도 없으면 완료로 본다.

| STEP 상태 구성 | `completed` |
| --- | --- |
| 전부 `COMPLETED` | `true` |
| `COMPLETED` + `SKIPPED` | `true` |
| `COMPLETED` + `PENDING` | `false` |
| `IN_PROGRESS` 가 하나라도 있음 | `false` |

### 점수의 분모가 다르다

| API | 분모 |
| --- | --- |
| `/quiz-results` | 그 Topic의 **전체 문제 수** — 학습 중 성취도 판단용 |
| `/review` | 실제로 **푼 문제 수** — 끝난 뒤 되돌아보는 용도 |

5문제 중 2문제만 풀고 1개를 맞혔다면 `/quiz-results` 는 20%, `/review` 는 50%다.
전자는 "아직 다 안 풀었다"를 점수로 드러내야 하고, 후자는 푼 것에 대한 평가다.

---

## 안 푼 문제의 정답을 감춘다

`/quiz-review` 와 `/review` 는 **정답과 해설을 담는 유일한 조회 API**다.
(생성·조회 API는 정답을 담지 않고, 답안 제출 응답에서만 공개한다.)

그래서 규칙을 둔다 — **답한 문제만 정답과 해설을 담는다.**

| 문제 상태 | `correctIndex` | `explanation` | `question`, `options` |
| --- | --- | --- | --- |
| 답했다 | 값이 있다 | 값이 있다 | 있다 |
| 안 풀었다 | `null` | `null` | 있다 |

답안 제출 시 이미 공개한 것을 다시 보여 주는 것은 괜찮다. 하지만 이 경로가
**문제를 풀기 전에 정답을 미리 보는 우회로가 되어서는 안 된다.**

문제 본문과 보기는 안 푼 문제에도 담는다 — 몇 문제 중 몇 개를 풀었는지 보여야 한다.

검증: `QuizReviewResponseTest`

---

## 마지막 회차만 본다

"새로운 퀴즈"(`POST /quizzes/regenerate`)를 만들면 같은 Topic에 회차가 쌓인다.
정리에서 전부 합치면 같은 범위의 문제가 두 벌 나오고, 문제 수도 실제로 푼 것보다
부풀어 보인다. 그래서 `/quiz-review` 와 `/review` 모두 **마지막 회차만** 담는다.

`/quiz-results` 도 같은 규칙이다. 화면이 방금 푼 것이 마지막 회차이기 때문이다.

새 회차를 만들고 아직 안 풀었다면 `answeredQuestions` 가 0이 된다 —
이전 회차의 답안이 새 회차에 섞여 들어오지 않는다.

---

## 틀린 문제 다시 풀기는 기록되지 않는다

**이 기능에는 API가 없다.** 화면에서만 동작한다.

답안은 최초 1회만 저장한다(`UNIQUE(session_id, quiz_id)`). 정답을 본 뒤 답을 고쳐
점수를 올릴 수 있으면 채점 기록이 성취도의 근거가 되지 못하기 때문이다.
`POST /quizzes/{quizId}/answer` 를 다시 불러도 기존 결과를 그대로 돌려준다.

그래서 "틀린 문제만 다시 풀기"는 서버를 부르지 않는다. `/quiz-review` 로 이미 받아 둔
`correctIndex` 로 화면에서 맞춰 보고, 결과를 저장하지 않는다. 처음 푼 기록은 그대로 남는다.

같은 범위에서 **다른 문제**를 풀고 싶다면 `POST /quizzes/regenerate` 다 — 그쪽은
새 회차를 만들고 AI를 부른다.
