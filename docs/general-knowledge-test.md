# 자료 미업로드 시 일반 지식 기반 생성 — 검증 기록

강의자료를 올리지 않아도 과목명과 시험 범위만으로 학습 주제·계획·퀴즈가 만들어지는지,
그리고 그렇게 만들었다는 사실이 사용자에게 드러나는지 확인한 기록이다.

- 대상: 1단계(일반 지식 기반 생성)
- 환경: `docker compose` (db + backend + frontend), `PUBLIC_PORT=8090`
- **AI 모드: `LLM_MODE=mock`, `QUIZ_AI_MODE=mock`** — 실제 Gemini 를 부르지 않는다.
  기능이 제대로 이어지는지는 목 데이터로도 전부 확인할 수 있고, 화면을 한 번 볼 때마다
  과금되는 것을 막아야 하기 때문이다. 실제 AI 호출 검증은 마지막에 최소 횟수만 따로 한다.

## 무엇을 확인하는가

이 기능은 "실패하지 않는 것"만으로는 부족하다. 자료 없이 만든 내용은 실제 수업 범위와
다를 수 있으므로, **그 사실이 화면에 남아 있어야** 한다. 그래서 세 가지를 나눠서 본다.

| | 확인 대상 |
| --- | --- |
| A | 자료 없이도 주제 → 계획 → 퀴즈까지 이어진다 |
| B | 근거가 아무것도 없으면(범위도 자료도 없음) 만들지 않고 거절한다 |
| C | 자료가 있으면 여전히 자료 기반으로 표시된다 |

## 검증 절차

### 사전 준비 — 스키마 적용

운영 프로파일은 `ddl-auto=validate` 다. 컬럼이 늘었으므로 새 이미지를 올리기 **전에**
마이그레이션을 먼저 적용해야 한다. 순서를 바꾸면 기동 중 `Schema-validation` 으로 죽는다.

```bash
docker compose exec -T db psql -U postgres -d naeil_study < docs/migrations/001-general-knowledge.sql
docker compose exec -T db psql -U postgres -d naeil_study < docs/migrations/002-study-chat.sql
docker compose up -d --build
```

실제로 순서를 바꿔 실행했을 때 다음과 같이 실패했다. 이 절차가 필요한 이유의 근거다.

```
SchemaManagementException: Schema-validation: missing column [exam_scope] in table [study_sessions]
dependency failed to start: container naeil-backend-1 is unhealthy
```

### 실행

```bash
LLM_MODE=mock QUIZ_AI_MODE=mock PUBLIC_PORT=8090 docker compose up -d --build
bash scripts/gk-verify.sh
```

요청은 전부 프론트엔드의 `/api` 프록시(`localhost:8090`)를 지난다. 백엔드 포트를 밖으로
열지 않으므로, 실제 사용자가 지나는 경로와 같은 경로로 확인하게 된다.

#### 검증 스크립트에서 걸린 두 가지 함정

둘 다 제품 결함이 아니라 Windows 셸 환경의 문제였지만, 같은 실수를 반복하지 않도록 남긴다.

| 증상 | 원인 | 대응 |
| --- | --- | --- |
| `400 INVALID_REQUEST` | 한글 본문을 셸 인자로 넘기면 콘솔 코드페이지에서 깨진다 | 본문을 UTF-8 파일로 쓰고 `--data-binary @file` |
| `curl: (26) Failed to open/read local data` | mingw curl 이 `-F "@/tmp/..."` 를 열지 못한다 | 작업 디렉터리를 Windows 경로로 |

## 결과 — 19건 전부 통과

```
== A. 자료 없이 시험 범위만으로 생성 ==
  세션: 3PMGPMJ5
  [PASS] A1 시험 정보 저장 200
  [PASS] A2 응답이 저장한 examScope 를 그대로 돌려준다
  [PASS] A3 분석 전 sourceType 은 null
  [PASS] A4 분석 전 grounded 는 false
  [PASS] A5 자료 없이 분석 성공 200
  [PASS] A6 Topic 이 1개 이상 만들어졌다 (topicCount=3)
  [PASS] A7 sourceType=GENERAL_KNOWLEDGE
  [PASS] A8 grounded=false
  주제: [목·일반지식] 3장 스택 / [목·일반지식] 4장 큐 / [목·일반지식] 5장 트리
  [PASS] A9 출처 문서를 지어내지 않았다 (source_document_ids=[])
  [PASS] A10 커리큘럼 생성 성공 (201)
  [PASS] A11 자료 없이도 퀴즈가 만들어진다 (201)
  [PASS] A12 문제가 실제로 들어 있다 (5문항)

== B. 근거가 아무것도 없으면 거절한다 ==
  응답: 400 {"code":"NO_PARSED_DOCUMENT", ...}
  [PASS] B1 시험 범위도 자료도 없으면 400
  [PASS] B2 code=NO_PARSED_DOCUMENT

== C. 자료가 있으면 자료 기반으로 표시된다 ==
  [PASS] C0 자료 업로드 성공
  [PASS] C1 텍스트 추출 성공
  [PASS] C2 자료 기반 분석 성공 (200)
  [PASS] C3 sourceType=USER_MATERIAL
  [PASS] C4 grounded=true

결과: PASS=19 FAIL=0
```

주제 제목의 `[목·일반지식]` 접두사는 `MockAiAnalysisClient` 가 붙인 것이다. 목 데이터를
실제 분석 결과로 착각하지 않도록 일부러 눈에 띄게 남긴다. `LLM_MODE=gemini` 에서는 나오지 않는다.

### A9 를 DB 로 확인한 이유

Topic 조회 응답에는 `sourceDocumentIds` 가 없다. 내부 식별자를 노출하지 않기 때문이다.
그래서 지어낸 문서 ID 가 남지 않았는지는 DB 를 직접 본다.

```sql
select source_document_ids from topics t
  join study_sessions s on s.id = t.session_id
 where s.session_code = '3PMGPMJ5';
--> []
```

일반 지식으로 만든 Topic 에는 근거로 삼을 문서가 없다. AI 가 문서 ID 를 지어내도
검증 단계에서 전부 버린다는 것을 여기서 확인한다.

## 검증 중에 고친 것

| 발견 | 조치 |
| --- | --- |
| `PUT /exam` 응답에 `examScope` 가 없어 저장 결과를 확인할 수 없었다 | `ExamResponse` 에 추가. 슬라이스 테스트와 통합 테스트로 못박음 |
| `NO_PARSED_DOCUMENT` 문구가 "자료를 올려라"만 안내했다 | "강의자료를 올리거나, 시험 범위를 입력해 주세요"로 수정 — 이제 빠져나갈 길이 두 개다 |
| 컬럼 추가분을 적용할 절차가 없었다 | `docs/migrations/001-general-knowledge.sql` 신설, `DEPLOY.md` 에 순서 명시 |

## 자동 테스트

수동 검증과 별개로 아래가 회귀를 막는다. **어느 것도 실제 AI 를 부르지 않는다.**

| 계층 | 내용 |
| --- | --- |
| `AnalysisServiceTest` | 자료가 없을 때 조각 분석을 건너뛰고 과목명·범위로 만든다 / 지어낸 출처 문서를 버린다 |
| `QuizGenerationServiceTest` | 자료가 없으면 거절하지 않고 Topic 기반 근거로 넘어간다 |
| `SessionControllerTest` | `examScope` 왕복, 분석 전 `sourceType=null` · `grounded=false` |
| `SessionApiIntegrationTest` | 실제 HTTP + DB 로 `examScope` 저장·조회 |
| `adapt.test.ts` (프론트) | `toSourceLabel()` — 안내 문구가 조용히 사라지지 않게 문자열까지 고정 |

## 실제 AI 로도 확인했다

**실제 Gemini 호출 검증은 따로 마쳤다** — `live-gemini-test.md`.
범위 3개가 주제 9개로 쪼개졌고, 그 주제로 퀴즈까지 만들어졌다.
