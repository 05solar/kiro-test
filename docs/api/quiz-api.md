# 퀴즈 API

학습을 완료한 Topic 에 대해 AI가 4지선다 객관식 문제를 만들고, 답안을 채점하고, 점수를 집계한다.

- Base URL: `/api/sessions/{sessionCode}`
- 인증: 없음. 8자리 세션 코드가 접근 키다.

관련 문서: [curriculum-api.md](curriculum-api.md) · [study-step-api.md](study-step-api.md) · [error-codes.md](error-codes.md)

## 목차

- [POST /topics/{topicId}/quizzes — 퀴즈 생성](#post-topicstopicidquizzes--퀴즈-생성)
- [GET /topics/{topicId}/quizzes — 퀴즈 조회](#get-topicstopicidquizzes--퀴즈-조회)
- [POST /quizzes/{quizId}/answer — 답안 제출](#post-quizzesquizidanswer--답안-제출)
- [GET /topics/{topicId}/quiz-results — 결과 집계](#get-topicstopicidquiz-results--결과-집계)
- [정답 노출 방지](#정답-노출-방지)
- [출제 방식](#출제-방식)
- [설정](#설정)

---

## POST /topics/{topicId}/quizzes — 퀴즈 생성

```http
POST /api/sessions/7K2M9QXF/topics/{topicId}/quizzes
```

Request Body가 없다. 출제 근거(강의자료, Topic, 학습 맥락)는 이미 DB에 있다.

### 생성 조건

| 조건 | 실패 시 |
| --- | --- |
| 세션이 존재한다 | 404 `SESSION_NOT_FOUND` |
| Topic이 이 세션 소유다 | 404 `TOPIC_NOT_FOUND` |
| 해당 Topic의 학습 단계가 `COMPLETED`다 | 409 `TOPIC_STUDY_NOT_COMPLETED` |
| 근거로 쓸 강의자료 텍스트가 있다 | 400 `NO_QUIZ_SOURCE_CONTEXT` |
| AI 생성과 응답 검증에 성공한다 | 502 `QUIZ_GENERATION_FAILED` |

계획에 들어가지 못한 Topic(시간 부족으로 미선택)과 `SKIPPED` 단계의 Topic도
학습 미완료로 본다. `REVIEW` 단계는 Topic이 없으므로 퀴즈 대상이 아니다.

### 멱등이다

이미 이 Topic의 퀴즈가 있으면 **AI를 다시 부르지 않고 기존 것을 돌려준다.**

| 상태 | 의미 |
| --- | --- |
| 201 | 이번 요청으로 새로 생성했다 |
| 200 | 기존 퀴즈를 그대로 돌려줬다 |

재생성 API는 이번 단계에서 만들지 않는다.

### 응답 — 201 Created / 200 OK

```json
{
  "topicId": "b10bd0b7-e8c7-47d7-af20-9e3d421881e2",
  "topicTitle": "CPU 스케줄링",
  "quizzes": [
    {
      "id": "52daee4a-9bd2-49ba-87b1-855fa5c646eb",
      "order": 1,
      "question": "Round Robin 스케줄링에서 Time Quantum의 역할로 가장 적절한 것은?",
      "options": [
        "각 프로세스에 동일한 시간 간격으로 CPU를 할당한다.",
        "가장 짧은 작업을 먼저 선택한다.",
        "우선순위가 가장 높은 프로세스만 실행한다.",
        "모든 프로세스를 동시에 실행한다."
      ],
      "difficulty": "MEDIUM"
    }
  ]
}
```

**`correctIndex`와 `explanation`이 없다. 실수가 아니다.** [정답 노출 방지](#정답-노출-방지) 참고.

문제 수는 기본 5개다. 강의자료 근거가 부족하면 AI가 3개까지 줄일 수 있다.
근거 없이 억지로 문제를 만들어 내지 않는다.

---

## GET /topics/{topicId}/quizzes — 퀴즈 조회

```http
GET /api/sessions/7K2M9QXF/topics/{topicId}/quizzes
```

응답 형식은 생성과 같다(정답 정보 없음). 아직 생성하지 않았으면 404 `QUIZ_NOT_FOUND`다.
빈 배열이 아니라 404를 주는 이유는 "생성 전"과 "문제가 0개"를 구분하기 위해서다.

---

## POST /quizzes/{quizId}/answer — 답안 제출

```http
POST /api/sessions/7K2M9QXF/quizzes/{quizId}/answer
Content-Type: application/json

{ "selectedIndex": 2 }
```

### 응답 — 200 OK

채점이 끝났으므로 여기서는 정답과 해설을 공개한다.

```json
{
  "quizId": "52daee4a-9bd2-49ba-87b1-855fa5c646eb",
  "selectedIndex": 2,
  "correctIndex": 0,
  "correct": false,
  "explanation": "Round Robin은 각 프로세스에 일정한 Time Quantum을 배정하고...",
  "answeredAt": "2026-08-27T22:10:31.123456"
}
```

`correct`는 **서버가** `selectedIndex == correctIndex`로 계산한다.
클라이언트가 정답 여부를 보내는 구조를 만들지 않는다.

### 답안은 최초 1회만 저장된다

같은 문제에 다시 제출하면 **첫 답안의 결과를 그대로 돌려준다** (200, 같은 형식).
DB의 `UNIQUE(session_id, quiz_id)`가 이를 보장한다. 정답을 본 뒤 답을 바꾸는 경로를
만들지 않는다 — 이 값이 다음 단계의 학습 성취도 판단 근거이기 때문이다.

### 실패

| 상태 | 코드 | 상황 |
| --- | --- | --- |
| 400 | `INVALID_QUIZ_OPTION` | `selectedIndex`가 0~3을 벗어남 |
| 400 | `INVALID_REQUEST` | `selectedIndex` 누락, 본문 형식 오류 |
| 404 | `QUIZ_NOT_FOUND` | 없거나 **다른 세션의 퀴즈** |

---

## GET /topics/{topicId}/quiz-results — 결과 집계

```http
GET /api/sessions/7K2M9QXF/topics/{topicId}/quiz-results
```

### 응답 — 200 OK

```json
{
  "topicId": "b10bd0b7-e8c7-47d7-af20-9e3d421881e2",
  "totalQuestions": 5,
  "answeredQuestions": 5,
  "correctAnswers": 4,
  "scorePercentage": 80,
  "completed": true,
  "results": [
    { "quizId": "...", "selectedIndex": 0, "correct": true, "answeredAt": "..." }
  ]
}
```

```
scorePercentage = round(correctAnswers / totalQuestions × 100)
completed       = (answeredQuestions == totalQuestions)
```

점수는 **전체 문제 수 기준**이다. 3문제만 풀고 2문제를 맞혔으면 40%다.
아직 다 풀지 않았으면 `completed = false`이며, 성취도 판단(다음 단계)은
`completed = true`인 경우에만 한다. 별도 상태 컬럼은 저장하지 않는다 — 매번 계산한다.

`results`에는 답한 문제만 담기고 정답 인덱스·해설은 없다. 채점 응답에서 이미 공개했고,
안 푼 문제의 정답이 새 나갈 경로를 만들지 않기 위해서다.

아직 퀴즈가 없으면 404 `QUIZ_NOT_FOUND`다.

---

## 정답 노출 방지

```
문제 조회 (생성/목록)    correctIndex, explanation 없음
답안 제출 후             그 문제의 correctIndex, explanation 공개
결과 집계                답한 문제의 정답 여부만. 인덱스·해설 없음
```

정답 정보가 문제와 함께 내려가면 클라이언트에서 정답을 미리 보거나 조작할 수 있다.
정답 공개는 항상 "그 문제에 답을 낸 뒤"다.

---

## 출제 방식

### 근거는 강의자료다 (Grounding)

```
Document.extractedText   사실의 출처. 이것에 근거한 문제만 만든다
StudyContext             출제 방향 조절 힌트. 사실의 출처가 아니다
```

일반 지식으로 강의자료에 없는 수치·정의·예외를 문제로 만들지 않는다.
프롬프트에서 시스템 규칙과 데이터를 태그로 분리하고, 자료 안의 지시문형 문장은
명령으로 취급하지 않는다(분석 단계와 같은 주입 방어).

### 관련 구간만 보낸다

문서 전체를 매번 AI에 보내지 않는다. `Topic.sourceDocumentIds`의 문서에서
Topic 제목·keyPoints 가 나타나는 문단 주변만 추출한다(`QuizContextExtractor`).
임베딩·벡터 검색은 쓰지 않으며, 이후 도입 시 이 컴포넌트만 교체한다.

### 학습 맥락 반영

| 맥락 | 반영 |
| --- | --- |
| 교수님 강조 | 자료에 근거가 있으면 최소 한 문제에서 점검 |
| 기출/예상 | 유사한 개념·풀이 유형을 가능하면 포함 |
| 취약 영역 | 암기 문제만이 아니라 개념 구분·적용 문제 포함 |
| 필수 범위 | 핵심 keyPoint를 빠뜨리지 않음 |

### 서버 검증

AI 응답을 그대로 저장하지 않는다. 하나라도 어긋나면 **전체 실패**다(502).
보기 3개짜리 문제를 "고쳐서" 내보내면 채점이 틀어지므로 분석처럼 보정하지 않는다.

```
문제 수 3 ~ 설정값
question / explanation 비어 있지 않음, 문제 2000자 이하
options 정확히 4개, 빈 값 없음, 정규화 후 중복 없음
correctIndex 0~3
difficulty EASY / MEDIUM / HARD
```

정답 self-check 는 같은 AI 요청의 시스템 프롬프트에서 요구한다. 별도 검증 호출을
추가하지 않는다 — 비용이 늘 뿐이다.

---

## 부수 효과

퀴즈 생성 / 조회 / 답안 제출 / 결과 집계는 모두 세션 활동이다.

```
lastAccessedAt = now
expiresAt      = now + 30일
```

세션·계획 상태는 바꾸지 않는다. 점수 기반 복습·재조정은 다음 단계의 일이다.

---

## 설정

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `QUIZ_QUESTIONS_PER_TOPIC` | `5` | Topic 하나당 생성할 문제 수 |
| `QUIZ_MAX_CONTEXT_CHARACTERS` | `20000` | AI에 보낼 추출 구간 최대 길이 |

AI 호출 자체는 분석과 같은 `AI_API_KEY` / `AI_MODEL` / `AI_TIMEOUT_SECONDS` /
`AI_MAX_RETRIES` 설정을 재사용한다.

---

## POST /api/sessions/{sessionCode}/topics/{topicId}/quizzes/regenerate — 새로운 퀴즈

같은 학습 범위로 **새 회차**의 문제를 만든다. Request Body 없다.

### "오답 다시 풀기"와 다른 기능이다

```
오답 다시 풀기    이미 낸 문제 중 틀린 것을 다시 본다.  AI 를 부르지 않는다
새로운 퀴즈 풀기  같은 범위에서 다른 문제를 만든다.     AI 를 부른다
```

### 기존 문제를 지우지 않는다

퀴즈에는 회차(`round`)가 있다. 새로 만들면 회차를 하나 올려 쌓는다.

```
Topic "스택"
├─ round 1  문제 1~5   답안 기록 그대로 남음
└─ round 2  문제 1~5   ← 이번에 만든 것
```

지난 회차와 답안 기록이 남아 있어야 무엇을 이미 풀었는지 알 수 있고,
다음 회차에서 중복을 피할 근거도 된다.

`(topic_id, round, quiz_order)` 가 유일하다. 회차를 빼고 잠그면
2회차의 1번 문제가 1회차의 1번과 부딪혀 저장에 실패한다.

### 중복을 어떻게 피하나

이전 회차에 낸 문제의 **문장만** AI 에 함께 보낸다.

```
보낸다      question
안 보낸다    options, correctIndex, explanation, 사용자 답안
```

보기·정답·해설은 중복 판단에 필요 없다. 전부 보내면 회차가 쌓일수록
프롬프트가 길어져 토큰만 늘어난다.

프롬프트에 넣는 조건:

```
- 위 문제와 같거나 매우 비슷한 문제를 만들지 않는다.
- 단어·숫자·이름만 바꾼 문제도 중복으로 본다.
- 같은 개념을 물어도 다른 문장, 다른 예시, 다른 질문 방식을 쓴다.
- 학습 범위는 그대로 유지한다.
```

### 응답 — 201 Created

`POST /quizzes` 와 같은 형식이다. 새 회차의 문제만 담긴다.

이후 `GET /quizzes` 와 `GET /quiz-results` 는 **마지막 회차**를 본다.
전부 합치면 2회차를 다 풀어도 "10문제 중 5문제"로 보이고 완료 판정도 나지 않는다.

### 실패

| 상태 | 코드 | 상황 |
| --- | --- | --- |
| 404 | `SESSION_NOT_FOUND` / `TOPIC_NOT_FOUND` | 없거나 다른 세션 |
| 409 | `TOPIC_STUDY_NOT_COMPLETED` | 학습 단계를 완료하지 않음 |
| 409 | `QUIZ_GENERATION_IN_PROGRESS` | 같은 Topic 의 생성이 이미 진행 중 |
| 422 | `NO_QUIZ_SOURCE_CONTEXT` | 근거로 쓸 강의자료 텍스트가 없음 |
| 502 | `QUIZ_GENERATION_FAILED` | AI 실패 / 응답 검증 실패 |

`QUIZ_GENERATION_IN_PROGRESS` 는 버튼을 두 번 누르거나 응답을 기다리다
새로고침한 경우다. 막지 않으면 AI 가 두 번 불려 그만큼 과금되고 회차도 둘로 갈린다.

---

## 개발 중에는 AI 를 부르지 않는다

`QUIZ_AI_MODE` 로 고른다.

| 값 | 동작 |
| --- | --- |
| `mock` (기본) | AI 를 부르지 않고 목 데이터를 만든다. 과금되지 않는다 |
| `gemini` | 실제 AI 를 부른다. `AI_PROVIDER` 설정을 따른다 |

기본이 `mock` 인 이유는 "새로운 퀴즈 만들기"처럼 여러 번 눌러 보게 되는 기능 때문이다.
화면을 한 번 확인할 때마다 과금되는 것보다, 실수로 꺼져 있는 편이 낫다.
mock 모드로 뜨면 기동 로그에 경고가 남는다.

**배포에서는 `gemini` 다.** `docker-compose.yml` 에 이미 들어 있다.

목 데이터도 검증기를 그대로 지난다 — 보기 4개, 정답 인덱스 0~3, 최소 문항 수.
회차마다 문항 번호를 밀어 "이전 문제를 받아 다른 문제가 나온다"는 경로도 확인할 수 있다.
