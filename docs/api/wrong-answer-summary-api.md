# 오답 복습 요약 API

Quiz 채점 결과에서 오답만 뽑아, 강의자료를 근거로 한 Topic 별 맞춤 복습 요약을 AI로 만든다.

- Base URL: `/api/sessions/{sessionCode}/wrong-answer-summary`
- 인증: 없음. 8자리 세션 코드가 접근 키다.

관련 문서: [quiz-api.md](quiz-api.md) · [error-codes.md](error-codes.md)

---

## POST — 요약 생성

```http
POST /api/sessions/7K2M9QXF/wrong-answer-summary
```

Request Body가 없다. 오답과 강의자료는 이미 서버 DB에 있고, 프론트가 채점 결과를
다시 보내는 구조를 만들지 않는다.

### 생성 조건

| 조건 | 실패 시 |
| --- | --- |
| 세션이 존재한다 | 404 `SESSION_NOT_FOUND` |
| 세션에 생성된 퀴즈가 있다 | 404 `QUIZ_NOT_FOUND` |
| 생성된 퀴즈를 전부 풀었다 | 409 `QUIZ_NOT_COMPLETED` |
| AI 생성과 응답 검증에 성공한다 | 502 `WRONG_ANSWER_SUMMARY_GENERATION_FAILED` |

절반만 푼 상태의 요약은 남은 문제를 푸는 순간 낡은 자료가 되므로, 대상 범위의
채점이 모두 끝난 뒤에만 만든다.

### 응답 — 201 Created / 200 OK

| 상태 | 의미 |
| --- | --- |
| 201 | 이번 요청에서 AI로 새로 생성했다 (재생성 포함) |
| 200 | 캐시된 기존 요약을 돌려줬다, 또는 오답이 없어 만들 것이 없다 |

```json
{
  "hasWrongAnswers": true,
  "wrongAnswerCount": 3,
  "generatedAt": "2026-08-27T22:00:00",
  "overallSummary": "CPU 스케줄링과 가상 메모리 개념을 우선 복습하세요.",
  "topics": [
    {
      "topicId": "b10bd0b7-e8c7-47d7-af20-9e3d421881e2",
      "topicTitle": "CPU 스케줄링",
      "wrongConcepts": ["Round Robin", "Time Quantum"],
      "summary": "Round Robin은 준비 큐의 프로세스들에게 일정한 Time Quantum을...",
      "keyReviewPoints": [
        "RR은 선점형 스케줄링이다.",
        "Time Quantum이 너무 크면 FCFS와 유사해진다."
      ],
      "priority": "VERY_HIGH"
    }
  ]
}
```

### 오답이 없으면 — 200, 오류가 아니다

전부 맞힌 세션은 복습할 오답이 없다. **AI를 호출하지 않고** 즉시 응답한다.

```json
{
  "hasWrongAnswers": false,
  "wrongAnswerCount": 0,
  "generatedAt": null,
  "overallSummary": null,
  "topics": []
}
```

### 캐시와 재생성

요약은 세션당 하나만 유지한다(`session_id` UNIQUE). 매 요청마다 AI를 부르지 않는다.

```
저장된 sourceLatestAnsweredAt == 현재 답안들의 최신 answeredAt  →  기존 요약 반환 (200)
새 답안이 생겨 최신 시각이 달라짐                              →  재생성 후 교체 (201)
```

재생성 중 AI가 실패하면 **기존 요약을 지우지 않는다.** 오래된 요약이라도 없는 것보다 낫다.
새 결과의 검증이 끝난 뒤에만 교체한다.

---

## GET — 요약 조회

```http
GET /api/sessions/7K2M9QXF/wrong-answer-summary
```

응답 형식은 생성과 같다. 아직 생성하지 않았으면 404 `WRONG_ANSWER_SUMMARY_NOT_FOUND`다.
조회는 저장된 값을 그대로 돌려줄 뿐 최신 여부를 다시 판단하지 않는다 — 갱신은 POST의 일이다.

---

## 요약을 만드는 방식

### 입력 데이터

```
QuizResult (isCorrect = false)   무엇을 틀렸는지. 정답 문제는 AI 입력에 포함하지 않는다
Quiz (question / options / explanation)
Topic (title / keyPoints / importance / 맥락 일치 여부)
Document.extractedText           사실의 출처 (관련 구간만 추출)
StudyContext                     무엇을 더 강조해 복습할지 결정하는 기준
```

사용자 답과 정답은 인덱스가 아니라 **보기의 실제 문자열**로 변환해 전달한다.
"2번을 골랐다"보다 "SJF를 골랐는데 정답은 Round Robin"이 의미를 안정적으로 전달한다.

### Topic별 그룹화와 참조값

오답을 Topic별로 묶고(원래 학습 순서 유지) 각 Topic에 `TOPIC_1` 형태의 참조값을 붙인다.
AI 응답도 이 참조값으로 Topic을 가리키며, 서버가 실제 UUID로 되돌린다.
**AI가 UUID나 Topic 제목을 만들어 내지 못한다** — 응답의 제목은 서버가 아는 값으로 대체한다.

### Grounding

각 Topic의 `sourceDocumentIds` 문서에서 관련 구간만 추출해 보낸다
(`QuizContextExtractor` 재사용, Topic당 길이 상한 별도). 요약의 사실적 근거는 이
구간이며, 강의자료에 없는 사실을 추가하지 않도록 프롬프트에서 요구한다.

프롬프트에는 다음도 명시한다:

```
사용자가 왜 오답을 골랐는지 심리적 이유를 추측하지 않는다.
틀린 문제를 통해 확인 가능한 개념적 보완 지점만 설명한다.
오답과 직접 관련 없는 Topic 은 포함하지 않는다.
```

### 서버 검증

AI 응답을 그대로 저장하지 않는다. 하나라도 어긋나면 전체 실패다(502).

```
topics 비어 있지 않음 / overallReview 비어 있지 않음
topicReference 는 요청에 제시된 값만, 중복 없음
wrongConcepts ≥ 1, summary 비어 있지 않음, keyReviewPoints ≥ 1
priority 는 VERY_HIGH / HIGH / MEDIUM
```

`LOW`가 없는 이유: 오답 요약에 들어온 것 자체가 이미 복습 대상이다.

---

## 바꾸지 않는 것

이 기능은 읽기 전용 분석 + 요약 저장이다. 다음을 수정하지 않는다.

```
Quiz / QuizResult / Topic / StudyContext (원본 데이터)
Curriculum / StudyStep / remainingStudyMinutes
StudySession.status
```

`lastAccessedAt` / `expiresAt` 갱신만 세션 활동으로 일어난다.

---

## 설정

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `WRONG_ANSWER_SUMMARY_MAX_CONTEXT_PER_TOPIC` | `8000` | Topic 하나당 AI에 보낼 추출 구간 최대 길이 |

AI 호출은 분석·퀴즈와 같은 `AI_API_KEY` / `AI_MODEL` / `AI_TIMEOUT_SECONDS` /
`AI_MAX_RETRIES` 설정을 재사용한다.
