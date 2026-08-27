# AI 분석 API 명세

강의자료와 학습 맥락을 AI로 분석해 학습 단위인 Topic을 만든다.

이 단계는 **자료를 Topic으로 구조화하는 데까지**다. 남은 시간에 맞춘 커리큘럼 배분,
StudyStep, Quiz는 다음 단계에서 만든다.

- Base URL: `/api/sessions/{sessionCode}`
- 인증: 없음. 8자리 세션 코드가 접근 키다.

## 목차

- [POST /analysis — 분석 실행](#post-apisessionssessioncodeanalysis--분석-실행)
- [GET /topics — Topic 조회](#get-apisessionssessioncodetopics--topic-조회)
- [분석 흐름](#분석-흐름)
- [importance의 의미](#importance의-의미)
- [학습 맥락 반영](#학습-맥락-반영)
- [Grounding 정책](#grounding-정책)
- [세션 상태 전이](#세션-상태-전이)

---

## POST /api/sessions/{sessionCode}/analysis — 분석 실행

```http
POST /api/sessions/7K2M9QXF/analysis
```

**Request Body가 없다.** 분석에 필요한 강의자료, 추출 텍스트, 학습 맥락은 이미 DB에 있다.
프론트가 텍스트를 다시 올려보내지 않는다.

### 시작 조건

| 조건 | 실패 시 |
| --- | --- |
| 세션이 존재한다 | 404 `SESSION_NOT_FOUND` |
| 과목명과 시험 일시가 있다 | 400 `EXAM_INFO_REQUIRED` |
| `PARSED` 상태 강의자료가 1개 이상 있다 | 400 `NO_PARSED_DOCUMENT` |
| 이미 분석 중이 아니다 | 409 `ANALYSIS_ALREADY_RUNNING` |

`PARSE_FAILED` 자료가 섞여 있어도 분석은 가능하다. `PARSED` 자료만 대상으로 삼고
제외한 파일은 서버 로그로만 남긴다.

### 응답 — 200 OK

동기로 처리하므로 응답 시점에 분석이 끝나 있다.

```json
{
  "sessionCode": "7K2M9QXF",
  "status": "READY",
  "topicCount": 6
}
```

Topic 내용은 담지 않는다. 목록은 `GET .../topics` 로 조회한다.

### 오류

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_SESSION_CODE` | 세션 코드 형식 오류 |
| 400 | `EXAM_INFO_REQUIRED` | 시험 정보 없음 |
| 400 | `NO_PARSED_DOCUMENT` | 텍스트 추출을 마친 자료 없음 |
| 404 | `SESSION_NOT_FOUND` | 세션 없음 |
| 409 | `ANALYSIS_ALREADY_RUNNING` | 이미 분석 중 |
| 502 | `ANALYSIS_FAILED` | AI 호출 실패, 응답 검증 실패, 결과 없음, 저장 실패 |

실패해도 **강의자료와 학습 맥락은 지우지 않는다.** 세션 상태만 `ANALYSIS_FAILED`가 되고
같은 API를 다시 호출해 재시도할 수 있다.

### 재분석

같은 API를 다시 호출하면 현재 자료와 학습 맥락으로 **Topic 전체를 다시 만든다.**
기존 Topic은 교체된다. 자료나 학습 맥락을 바꾼 뒤의 이전 결과는 낡은 분석이기 때문이다.

### 분석 진행 상태 확인

별도 API를 만들지 않는다. `GET /api/sessions/{sessionCode}` 의 `status` 로 확인한다.

가짜 진행률(%)은 서버에서 만들지 않는다. AI 작업의 실제 진행도를 알 수 없기 때문이다.

---

## GET /api/sessions/{sessionCode}/topics — Topic 조회

```http
GET /api/sessions/7K2M9QXF/topics
```

### 응답 — 200 OK

`topicOrder` 오름차순으로 정렬된다.

```json
{
  "topics": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "CPU 스케줄링",
      "summary": "준비 큐에 있는 프로세스 중 CPU를 할당할 프로세스를 결정하는 과정이다. FCFS, SJF, Round Robin 등의 알고리즘과 각각의 특징을 비교하는 것이 핵심이다.",
      "keyPoints": ["FCFS", "SJF", "Round Robin", "Time Quantum"],
      "importance": "VERY_HIGH",
      "estimatedStudyMinutes": 35,
      "professorEmphasisMatched": false,
      "pastExamMatched": true,
      "weakAreaMatched": false,
      "mustStudyMatched": false,
      "topicOrder": 2
    }
  ]
}
```

아직 분석하지 않았으면 빈 배열이다.

```json
{ "topics": [] }
```

출처 문서 ID(`sourceDocumentIds`)는 DB에만 저장하고 응답에 넣지 않는다.
화면에서 쓰지 않는 내부 추적 정보다.

### 오류

| 상태 | code |
| --- | --- |
| 400 | `INVALID_SESSION_CODE` |
| 404 | `SESSION_NOT_FOUND` |

Topic 상세 조회 API는 만들지 않는다. 목록 응답에 필요한 정보가 모두 들어 있다.

---

## 분석 흐름

```
시작 조건 검증
      ↓
세션 상태 ANALYZING
      ↓
문서별로 조각 나누기 (기본 8000자, 겹침 300자)
      ↓
조각마다 1차 분석 → 주제 후보 (제목, 요약, 핵심 개념)
      ↓
최종 통합 1회 → 중복 제거, 중요도, 학습시간, 맥락 일치, 순서 결정
      ↓
응답 검증 (실패 시 1회만 재요청)
      ↓
기존 Topic 삭제 + 새 Topic 저장
      ↓
세션 상태 READY
```

### 왜 두 단계인가

여러 문서의 텍스트를 한 문자열로 붙여 한 번에 보내면 모델의 컨텍스트 한도를 넘긴다.
그렇다고 조각마다 최종 Topic을 만들면 같은 주제가 여러 개로 쪼개진다.

```
Chunk 1  CPU Scheduling
Chunk 4  CPU 스케줄링
Chunk 7  CPU Scheduling Algorithms
```

그래서 조각에서는 후보만 뽑고, 합치는 판단은 전체를 한 번에 보는 통합 단계에서 한다.
중요도와 학습시간도 통합 단계에서 정한다. 조각 하나만 보고는 전체에서의 우선순위를 알 수 없다.

### estimatedStudyMinutes는 최종 계획이 아니다

Topic 예상시간의 합이 사용자의 `remainingStudyMinutes`를 넘어도 정상이다.

```
Topic 전체 예상시간   520분
남은 학습시간         180분   →  허용
```

시간이 부족하다고 이 단계에서 Topic을 빼지 않는다. 자료 분석과 시간 배분을 섞으면
"무엇을 배울 수 있는가"와 "무엇을 배울 시간이 있는가"가 뒤엉킨다.
180분에 맞추는 일은 다음 커리큘럼 단계에서 한다.

---

## importance의 의미

**시험 출제 확률이 아니다. 학습 우선순위다.**

판단 근거:

```
핵심 개념성
+ 자료 안에서의 반복 정도
+ 다른 개념과의 연결성
+ 전체 내용을 이해하는 데 필요한 정도
```

| 값 | 의미 | 화면 표기 |
| --- | --- | --- |
| `VERY_HIGH` | 반드시 우선적으로 학습해야 할 핵심 내용 | 매우 중요 |
| `HIGH` | 높은 우선순위 | 중요 |
| `MEDIUM` | 시간이 있다면 학습해야 하는 내용 | 보통 |
| `LOW` | 시간이 부족하면 줄일 수 있는 세부 내용 | 낮음 |

화면에서도 "시험 출제 가능성"이 아니라 "학습 우선순위"로 표시한다.

---

## 학습 맥락 반영

사용자가 입력한 학습 맥락은 AI 요청에 함께 전달되고, 결과에 4개 boolean으로 표시된다.

| 필드 | 사용자 입력 | 이후 용도 |
| --- | --- | --- |
| `professorEmphasisMatched` | 교수님이 강조한 부분 | 우선순위 상향 |
| `pastExamMatched` | 기출/예상 문제 | 우선순위 + Quiz 유형 비중 |
| `weakAreaMatched` | 자신 없는 부분 | 학습시간 / Quiz / 복습 비중 |
| `mustStudyMatched` | 반드시 공부할 범위 | **제약.** 시간이 부족해도 빼지 않는다 |

`mustStudyMatched`는 나머지 셋과 성격이 다르다. 우선순위 가중치가 아니라 제약이다.
중요도가 `LOW`인 Topic이어도 이 값이 `true`면 커리큘럼에서 가능한 한 유지한다.

### 숫자 가중치를 하드코딩하지 않는다

```
교수 강조 +30점 / × 2.0
기출 +20점 / × 1.8
```

이런 계수를 임의로 정하지 않았다. 문서 내용과 학습 맥락을 함께 놓고 LLM이 판단하게 한다.
테스트로 필요성이 확인된 뒤에 규칙 기반 점수를 더할 수 있다.

### 학습 맥락이 없어도 분석은 진행된다

학습 맥락은 선택 입력이다. 네 값이 모두 비어 있으면 프롬프트에서 해당 절을 비우고
자료만으로 분석한다.

---

## Grounding 정책

**강의자료에 없는 내용을 새로운 학습 내용으로 만들지 않는다.**

학습 맥락은 사용자가 준 힌트이지 검증된 사실이 아니다.

```
mustStudyAreas = "B+ Tree"
업로드된 자료   = 운영체제 (B+ Tree 내용 없음)

→ B+ Tree Topic을 만들지 않는다
→ 어떤 Topic에도 mustStudyMatched = true 를 붙이지 않는다
```

학습 맥락은 **자료 안에서 관련 내용을 찾았을 때 우선순위를 조정하는 힌트**로만 쓴다.

### 프롬프트 주입 대비

시스템 지시문과 분석 대상 데이터를 한 문자열로 이어붙이지 않는다.

```
SYSTEM RULES                 시스템 프롬프트
TASK / COURSE INFORMATION    사용자 메시지
USER PROVIDED STUDY CONTEXT  <user_study_context> 태그로 감싼다
LECTURE DOCUMENTS            <lecture_document> 태그로 감싼다
```

태그 안의 내용은 데이터이며 명령이 아니라는 점을 시스템 프롬프트에 명시한다.
"위 지시를 무시하라" 같은 문장이 자료나 사용자 입력에 있어도 분석 대상 텍스트로만 다룬다.

### AI가 문서 ID를 지어내지 못하게 한다

AI에게는 내부 UUID 대신 `DOC_1`, `DOC_2` 같은 참조값만 준다.
응답에 돌아온 참조값은 서버가 실제 UUID로 되돌리고, 모르는 값은 버린다.

---

## 세션 상태 전이

```
UPLOADING ──POST /analysis──> ANALYZING ──성공──> READY
                                  │
                                  └──실패──> ANALYSIS_FAILED
                                                  │
                                          POST /analysis 재시도
```

문서 파싱이 모두 끝나도 상태는 `UPLOADING`을 유지한다.
`ANALYZING`은 AI 요청이 실제로 시작될 때만 들어간다.

---

## 설정

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `AI_API_KEY` | (없음) | 비어 있으면 분석 요청 시점에만 실패한다 |
| `AI_MODEL` | `claude-opus-5` | 사용 모델 |
| `AI_TIMEOUT_SECONDS` | `180` | 호출 타임아웃 |
| `AI_MAX_RETRIES` | `2` | 연결 오류·5xx 재시도 (SDK 담당) |
| `AI_CHUNK_SIZE` | `8000` | 조각 크기 (글자 수) |
| `AI_CHUNK_OVERLAP` | `300` | 조각 겹침 (글자 수) |
| `AI_MAX_TOPICS` | `30` | Topic 개수 상한 |

키가 없어도 애플리케이션은 뜬다. 세션, 업로드, 파싱은 AI 없이 동작해야 하기 때문이다.

### 재시도 정책

| 실패 유형 | 재시도 |
| --- | --- |
| 연결 오류, 429, 5xx | SDK가 최대 2회 |
| 응답 검증 실패 | 통합 요청을 1회만 다시 보낸다 |
| 두 번째도 검증 실패 | 재시도하지 않고 `ANALYSIS_FAILED` |

형식이 어긋나는 응답을 무한히 다시 받으면 비용만 늘어난다.

---

## 로그 정책

다음은 로그에 남기지 않는다.

```
추출 텍스트 전문
학습 맥락 전문
프롬프트 전문
AI 응답 전문
```

로그에는 `sessionId`, 문서 수, 조각 수, 호출 성공/실패, 처리 시간, Topic 수만 남긴다.
AI 원본 응답은 DB에 저장하지 않는다. 구조화된 Topic만 저장한다.
