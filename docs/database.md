# DB 구조

PostgreSQL. 스키마는 JPA(`ddl-auto`)로 생성한다. 아래 DDL은 실제 PostgreSQL 16에서 생성된 결과다.

## 현재 테이블

현재 테이블은 열 개다. **`users` 테이블은 존재하지 않는다.**

```
study_sessions   documents   study_contexts   topics   curriculums   study_steps
quizzes   quiz_results   wrong_answer_summaries   chat_messages
```

아래에는 앞의 여섯 개를 설명한다. 나머지 네 개의 정확한 정의는
[schema.sql](schema.sql)(개발 DB 에서 `pg_dump` 로 뽑은 것)과 각 API 명세를 본다.

### study_sessions

```
         Column          |              Type              | Nullable
-------------------------+--------------------------------+----------
 id                      | uuid                           | not null
 session_code            | character varying(8)           | not null
 subject                 | character varying(255)         |
 exam_scope              | text                           |
 exam_at                 | timestamp(6) without time zone |
 available_study_minutes | integer                        |
 remaining_study_minutes | integer                        |
 status                  | character varying(20)          | not null
 source_type             | character varying(30)          |
 current_step_order      | integer                        |
 created_at              | timestamp(6) without time zone | not null
 updated_at              | timestamp(6) without time zone | not null
 last_accessed_at        | timestamp(6) without time zone | not null
 expires_at              | timestamp(6) without time zone | not null

Indexes:
    "study_sessions_pkey" PRIMARY KEY, btree (id)
    "uk_study_sessions_session_code" UNIQUE CONSTRAINT, btree (session_code)

Check constraints:
    "study_sessions_status_check" CHECK (status IN
      ('CREATED','UPLOADING','ANALYZING','ANALYSIS_FAILED','READY','IN_PROGRESS','COMPLETED','EXPIRED'))
```

### 컬럼 설명

| 컬럼 | 설명 |
| --- | --- |
| `id` | 내부 식별자(UUID). 다른 테이블과의 FK에만 쓰고 **API 응답에 노출하지 않는다** |
| `session_code` | 사용자가 입력하는 8자리 접근 키. UNIQUE |
| `subject` | 과목명. 시험 정보 입력 시 채운다 |
| `exam_scope` | 시험 범위. 선택 입력이지만, **강의자료를 올리지 않으면 학습 내용을 만드는 유일한 근거**가 된다 |
| `exam_at` | 시험 일시. 시험 정보 입력 시 채운다 |
| `available_study_minutes` | 사용자가 입력한 **전체** 학습 가능 시간(분). 진행 중 변경하지 않는다 |
| `remaining_study_minutes` | 현재 **남아 있는** 학습 가능 시간(분). 시험 정보 입력 시 `min(입력값, 시험까지 남은 분)`으로 초기화되고, 이후 STEP 완료마다 감소한다 |
| `status` | 세션 상태 Enum (문자열 저장) |
| `source_type` | 학습 내용의 근거. `USER_MATERIAL` / `GENERAL_KNOWLEDGE`. 분석을 시작할 때 정해지고, 그 전에는 NULL |
| `current_step_order` | 현재 진행 중인 STEP 순번. 다른 기기에서 복구할 때 사용 |
| `created_at` | 생성 시각 |
| `updated_at` | 마지막 변경 시각 |
| `last_accessed_at` | 마지막 접근 시각. 조회 성공 시마다 갱신 |
| `expires_at` | 보관 만료 시각 = `last_accessed_at + 30일` |

### 식별자를 둘로 나눈 이유

```
id            UUID       내부 관계용. 추측 불가하고 노출되지 않는다
session_code  VARCHAR(8) 사용자 노출용 접근 키
```

세션 코드는 사용자가 입력해야 하므로 짧아야 하고, 짧으면 추측 가능성이 생긴다.
그래서 테이블 간 연관관계는 UUID로 맺고, 코드는 접근 경로로만 쓴다.
코드가 바뀌어도 다른 테이블의 FK는 영향을 받지 않는다.

### 시간 컬럼이 네 개인 이유

| 컬럼 | 답하는 질문 |
| --- | --- |
| `created_at` | 언제 만들어졌나 |
| `updated_at` | 마지막으로 무언가 바뀐 게 언제인가 |
| `last_accessed_at` | 사용자가 마지막으로 들어온 게 언제인가 |
| `expires_at` | 언제 지워도 되나 |

만료 기준이 "마지막 접근 후 30일"이므로 `last_accessed_at`과 `expires_at`이 함께 갱신된다.
`expires_at`을 따로 저장해 두면 삭제 배치가 `WHERE expires_at < now()` 한 줄로 끝난다.

### documents

```
       Column       |              Type              | Nullable
--------------------+--------------------------------+----------
 id                 | uuid                           | not null
 session_id         | uuid                           | not null
 original_file_name | character varying(255)         | not null
 stored_file_name   | character varying(100)         | not null
 storage_path       | character varying(500)         | not null
 file_type          | character varying(10)          | not null
 file_size          | bigint                         | not null
 status             | character varying(20)          | not null
 extracted_text     | text                           |
 character_count    | integer                        |
 parsed_at          | timestamp(6) without time zone |
 parse_error_message| character varying(500)         |
 created_at         | timestamp(6) without time zone | not null
 updated_at         | timestamp(6) without time zone | not null

Indexes:
    "documents_pkey" PRIMARY KEY, btree (id)

Foreign-key constraints:
    session_id REFERENCES study_sessions(id)

Check constraints:
    file_type IN ('PDF','DOCX','TXT')
    status    IN ('UPLOADED','PARSING','PARSED','PARSE_FAILED')
```

| 컬럼 | 설명 |
| --- | --- |
| `session_id` | 소유 세션. **세션 코드가 아니라 내부 UUID**를 참조한다 |
| `original_file_name` | 사용자가 올린 이름. 화면 표시용. 저장 경로에 쓰지 않는다 |
| `stored_file_name` | Storage 안의 실제 이름 (UUID + 확장자) |
| `storage_path` | Storage root 기준 상대 경로. 4단계 파서가 이 값으로 원본을 읽는다 |
| `file_type` | PDF / DOCX / TXT |
| `file_size` | byte. 세션 전체 용량 제한 계산에 쓴다 |
| `status` | 텍스트 추출 상태 (`UPLOADED` / `PARSING` / `PARSED` / `PARSE_FAILED`) |
| `extracted_text` | 파일에서 추출한 텍스트. `PARSED` 일 때만 값이 있다. AI 분석의 입력이다 |
| `character_count` | 추출한 텍스트 길이 |
| `parsed_at` | 파싱 성공 시각. 실패하거나 미파싱이면 null |
| `parse_error_message` | 실패 원인 요약. 내부 진단용이며 API 응답에 넣지 않는다 |

파일 본문(바이너리)은 DB에 넣지 않는다. 위치만 저장하고 실제 파일은 Storage에 둔다.
추출한 텍스트는 AI 분석 입력이라 DB에 저장한다.

**`extracted_text` 에 `@Lob` 을 붙이지 않는다.** PostgreSQL에서 `@Lob String` 은
JDBC 드라이버가 large object로 저장해, 컬럼에 본문 대신 OID 숫자(`16411` 같은 값)가 들어간다.
H2에서는 정상 동작하기 때문에 테스트로는 드러나지 않는다.
`columnDefinition = "TEXT"` 만으로 길이 제한 없는 컬럼이 만들어진다.

**파일명을 둘로 나눈 이유** — 사용자가 올린 이름을 그대로 저장 경로에 쓰면
`../../../etc/passwd` 같은 경로 조작과 파일명 충돌이 모두 가능해진다.
표시용 이름과 저장용 이름을 분리하면 두 문제가 함께 사라진다.

**연관관계** — Document → StudySession 단방향(`@ManyToOne(LAZY)`)이다.
세션이 자기 문서를 알아야 할 일이 아직 없어 양방향으로 만들지 않았다.

### study_contexts

```
       Column       |              Type              | Nullable
--------------------+--------------------------------+----------
 id                 | uuid                           | not null
 session_id         | uuid                           | not null
 professor_emphasis | text                           |
 past_exam_info     | text                           |
 weak_areas         | text                           |
 must_study_areas   | text                           |
 created_at         | timestamp(6) without time zone | not null
 updated_at         | timestamp(6) without time zone | not null

Indexes:
    "study_contexts_pkey" PRIMARY KEY, btree (id)
    "uk_study_contexts_session_id" UNIQUE CONSTRAINT, btree (session_id)

Foreign-key constraints:
    session_id REFERENCES study_sessions(id)
```

| 컬럼 | 설명 |
| --- | --- |
| `session_id` | 소유 세션. **UNIQUE** 라서 세션당 하나만 존재한다 |
| `professor_emphasis` | 교수님이 강조한 부분 |
| `past_exam_info` | 기출/예상 문제 |
| `weak_areas` | 사용자가 자신 없는 부분 |
| `must_study_areas` | 반드시 공부할 범위. 이후 커리큘럼에서 **제약**으로 다룬다 |

네 항목 모두 선택 입력이다. 하나도 입력하지 않아도 이후 기능이 정상 동작해야 한다.
공백만 입력한 값은 저장 시점에 `null` 로 정규화한다.

**`session_id` 에 UNIQUE 를 건 이유** — 세션당 학습 맥락이 둘 이상 생기면
어느 것이 최신인지 코드가 판단해야 한다. 제약으로 막으면 그 판단 자체가 필요 없어진다.
저장은 Upsert로 처리한다.

**연관관계** — StudyContext → StudySession 단방향 `@OneToOne(LAZY)`.
Document와 직접 연결하지 않는다. 학습 맥락은 특정 문서가 아니라 세션 전체에 대한 정보다.

### topics

```
           Column           |              Type              | Nullable
----------------------------+--------------------------------+----------
 id                         | uuid                           | not null
 session_id                 | uuid                           | not null
 title                      | character varying(200)         | not null
 summary                    | text                           | not null
 key_points                 | jsonb                          | not null
 importance                 | character varying(20)          | not null
 estimated_study_minutes    | integer                        | not null
 professor_emphasis_matched | boolean                        | not null
 past_exam_matched          | boolean                        | not null
 weak_area_matched          | boolean                        | not null
 must_study_matched         | boolean                        | not null
 source_document_ids        | jsonb                          | not null
 topic_order                | integer                        | not null
 created_at                 | timestamp(6) without time zone | not null
 updated_at                 | timestamp(6) without time zone | not null

Indexes:
    "topics_pkey" PRIMARY KEY, btree (id)

Foreign-key constraints:
    session_id REFERENCES study_sessions(id)

Check constraints:
    importance IN ('VERY_HIGH','HIGH','MEDIUM','LOW')
```

| 컬럼 | 설명 |
| --- | --- |
| `title` | 주제 이름. 문장이 아니라 이름이다 |
| `summary` | 시험 직전 이해해야 할 핵심 요약. 강의자료에 없는 사실을 담지 않는다 |
| `key_points` | 핵심 개념 목록 (JSON 배열) |
| `importance` | **학습 우선순위.** 시험 출제 확률이 아니다 |
| `estimated_study_minutes` | 이 주제 학습에 필요한 시간(분). 남은 시간에 맞춘 조정 **전** 값 |
| `*_matched` | 사용자 학습 맥락과의 관련 여부 4종 |
| `source_document_ids` | 이 주제가 나온 문서 UUID 목록 (JSON 배열) |
| `topic_order` | 개념 의존 관계를 반영한 기본 학습 순서. 최종 커리큘럼 순서가 아니다 |

**시간 합이 `remaining_study_minutes` 를 넘어도 정상이다.** 이 단계는 "무엇을 배울 수
있는가"를 정하고, "무엇을 배울 시간이 있는가"는 커리큘럼 단계에서 정한다.

**`must_study_matched` 는 다른 세 boolean과 성격이 다르다.** 우선순위 가중치가 아니라
제약이다. 커리큘럼 단계에서 시간이 부족해도 이 주제는 가능한 한 남긴다.

**JSON 컬럼에 `columnDefinition` 을 적지 않는다.** `@JdbcTypeCode(SqlTypes.JSON)` 만 붙이고
타입은 Dialect가 고르게 둔다 (PostgreSQL은 `jsonb`, H2는 `json`).
`columnDefinition = "jsonb"` 를 박으면 H2에서 DDL이 깨진다.

**분석 결과는 통째로 교체한다.** 재분석 시 세션의 Topic을 모두 지우고 새로 넣는다.
개별 갱신을 하지 않으므로 Topic ID는 재분석마다 바뀐다.

### curriculums

```
          Column           |              Type              | Nullable
---------------------------+--------------------------------+----------
 id                        | uuid                           | not null
 session_id                | uuid                           | not null
 initial_remaining_minutes | integer                        | not null
 total_allocated_minutes   | integer                        | not null
 status                    | character varying(20)          | not null
 created_at                | timestamp(6) without time zone | not null
 updated_at                | timestamp(6) without time zone | not null

Indexes:
    "curriculums_pkey" PRIMARY KEY, btree (id)
    "uk_curriculums_session_id" UNIQUE CONSTRAINT, btree (session_id)

Foreign-key constraints:
    session_id REFERENCES study_sessions(id)

Check constraints:
    status IN ('CREATED','IN_PROGRESS','COMPLETED')
```

| 컬럼 | 설명 |
| --- | --- |
| `session_id` | 소유 세션. **UNIQUE** 라서 세션당 하나만 존재한다 |
| `initial_remaining_minutes` | 계획을 세운 시점의 남은 학습 시간. 이후 불변 |
| `total_allocated_minutes` | 배정 시간의 합. 항상 위 값 이하 |

**두 시간을 나눈 이유** — 학습이 진행되면 세션의 `remaining_study_minutes` 는 줄어든다.
그때도 "처음에 몇 분을 기준으로 계획했는가"를 알 수 있어야 계획 대비 실제를 비교할 수 있다.

### study_steps

```
           Column           |              Type              | Nullable
----------------------------+--------------------------------+----------
 id                         | uuid                           | not null
 curriculum_id              | uuid                           | not null
 topic_id                   | uuid                           |
 step_order                 | integer                        | not null
 type                       | character varying(20)          | not null
 title                      | character varying(200)         | not null
 allocated_minutes          | integer                        | not null
 original_estimated_minutes | integer                        | not null
 status                     | character varying(20)          | not null
 is_mandatory               | boolean                        | not null
 priority_reasons           | jsonb                          | not null
 started_at                 | timestamp(6) without time zone |
 completed_at               | timestamp(6) without time zone |
 actual_study_minutes       | integer                        |
 created_at                 | timestamp(6) without time zone | not null
 updated_at                 | timestamp(6) without time zone | not null

Indexes:
    "study_steps_pkey" PRIMARY KEY, btree (id)

Foreign-key constraints:
    curriculum_id REFERENCES curriculums(id)
    topic_id      REFERENCES topics(id)

Check constraints:
    type   IN ('STUDY','REVIEW')
    status IN ('PENDING','IN_PROGRESS','COMPLETED','SKIPPED')
```

| 컬럼 | 설명 |
| --- | --- |
| `topic_id` | 학습 대상 주제. `REVIEW` 단계에는 없어서 nullable |
| `allocated_minutes` | 이번 계획에서 배정한 시간. 남은 시간에 맞춰 줄어들 수 있다 |
| `original_estimated_minutes` | 계획 시점 Topic의 권장 학습시간 복사본. 이후 불변 |
| `is_mandatory` | 사용자가 반드시 학습하겠다고 밝힌 범위인지 |
| `priority_reasons` | 계획에 포함된 이유 (JSON 배열). 화면 표시용이며 계산에 쓰지 않는다 |
| `started_at` | 학습을 시작한 시각 |
| `completed_at` | 학습을 마친 시각 |
| `actual_study_minutes` | 실제로 쓴 시간(분). `ceil((completed_at - started_at) / 60초)` |

**시간 값 세 개를 분리한 이유**

```
original_estimated_minutes  제대로 학습하는 데 필요하다고 본 시간
allocated_minutes           이번 계획에서 배정한 시간
actual_study_minutes        실제로 쓴 시간
```

예상 60분인 주제에 40분을 배정했고 실제로 52분이 걸렸다면 "계획보다 12분 초과"를 알 수 있다.
세 값을 하나로 합치면 이 판단이 불가능해지고, 동적 재조정의 근거도 사라진다.

**Topic 값을 복사해 두는 이유** — Topic은 재분석으로 통째로 교체된다.
계획을 세운 시점의 기준값이 남아 있어야 계획 대비 실제를 비교할 수 있다.

**`actual_study_minutes`는 자르지 않는다** — 배정 30분에 실제 62분이 걸렸으면 62를 그대로 넣는다.
30으로 잘라 저장하면 "계획보다 32분 초과"라는 사실이 사라진다.

**한 계획에서 `IN_PROGRESS`인 행은 하나 이하다.** DB 제약이 아니라 서비스가 보장한다.
두 단계가 동시에 진행 중이면 같은 시간이 양쪽 `actual_study_minutes`에 기록된다.

## 향후 ERD

이후 단계에서 추가될 전체 구조.

```
StudySession
     │
     ├──── 1:0..1 ─ StudyContext   (구현 완료)
     │
     ├──── 1:N ─── Document        (구현 완료)
     │
     ├──── 1:N ─── Topic           (구현 완료)
     │                 │
     │                 └── 1:N ── Quiz        (STEP 9)
     │
     ├──── 1:0..1 ─ Curriculum     (구현 완료)
     │                 │
     │                 └── 1:N ── StudyStep   (구현 완료)
     │
     └──── 1:N ─── QuizResult      (STEP 9)
```

세션이 삭제되면 위 데이터와 Object Storage의 파일까지 함께 삭제한다.

## 동적 커리큘럼을 위한 구조

```
StudyStep 완료
      ↓
실제 학습시간 계산 (started_at ~ completed_at)        ← 구현 완료
      ↓
study_sessions.remaining_study_minutes 갱신           ← STEP 9
      ↓
시험까지 남은 실제 시간 확인 (exam_at - now)          ← STEP 9
      ↓
남은 StudyStep 조회 (status = PENDING)                ← STEP 9
      ↓
남은 시간에 맞게 뒤쪽 STEP의 allocated_minutes 재배분  ← STEP 9
```

**완료 시 `remaining_study_minutes`를 단순 차감하지 않는다.**

```
remaining = 180
actual    = 50
remaining = 130   ← 이렇게만 하지 않는다
```

`actual_study_minutes`에는 사용자가 브라우저를 닫아 둔 시간까지 들어간다.
그 값을 그대로 빼면 남은 학습 시간이 시험까지 남은 실제 시간과 어긋난다.
현재 시각과 `exam_at`을 함께 보고 다시 계산해야 하며, 그것이 STEP 9의 일이다.

`available_study_minutes`는 어느 단계에서도 건드리지 않는다.
최초 계획 대비 얼마나 초과/단축했는지 계산하려면 원본 기준값이 남아 있어야 한다.

### 상태 전이 정리

```
study_steps.status     PENDING → IN_PROGRESS → COMPLETED
curriculums.status     CREATED → IN_PROGRESS → COMPLETED
study_sessions.status  READY   → IN_PROGRESS
```

`current_step_order`는 **현재 진행 중인 단계**를 뜻한다.
단계를 시작하면 그 순번이 들어가고, 완료하면 `null`이 된다.
완료 직후 다음 순번을 미리 넣으면 "진행 중"과 "다음 차례"가 구분되지 않는다.

계획이 `COMPLETED`가 되어도 세션은 `IN_PROGRESS`로 남는다.
이후 퀴즈와 최종 복습이 남아 있어 세션이 끝났다고 볼 수 없기 때문이다.

## 스키마 관리 방식

| 환경 | ddl-auto | 비고 |
| --- | --- | --- |
| 로컬 개발 | `update` | 기본값 |
| 테스트 | `create-drop` | H2 인메모리 |
| 운영 | `validate` | 마이그레이션 도구 도입 후 전환 (TODO) |

MVP 단계에서는 스키마가 자주 바뀌므로 `update`를 쓴다.
스키마가 안정되면 Flyway 도입을 검토한다.
