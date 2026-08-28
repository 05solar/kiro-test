# 스키마 마이그레이션 — 검증 기록

EC2 에 새 버전을 올릴 때 기동이 죽지 않는지 확인한 기록이다.

- 환경: `docker compose` (db + backend + frontend), `PUBLIC_PORT=8090`
- AI 모드: `LLM_MODE=mock` — 이 검증에 AI 는 필요 없다. 실제 호출 없음.

## 무엇이 문제였나

마이그레이션이 **배포 밖에** 있었다.

```
1. psql 로 마이그레이션 적용    ← 사람이
2. 새 이미지 배포               ← 사람이
```

두 단계의 순서를 사람이 지켜야 했고, 2번을 먼저 하면 컬럼이 없는 채로 기동해 죽었다.
1단계 배포 때 실제로 겪었다.

```
SchemaManagementException: Schema-validation: missing column [exam_scope] in table [study_sessions]
dependency failed to start: container naeil-backend-1 is unhealthy
```

`restart: unless-stopped` 라 컨테이너는 재시작을 반복하고, `depends_on: service_healthy`
때문에 프론트까지 뜨지 않는다. **서비스 전체가 내려간다.**

## 어떻게 고쳤나

Flyway 를 넣어 애플리케이션이 뜨면서 스스로 적용하게 했다.

```
backend 기동 → Flyway 가 밀린 마이그레이션 적용 → Hibernate 가 결과를 검증 → 서비스 시작
```

Flyway 는 Hibernate 검증보다 **먼저, 같은 기동 안에서** 돈다. 순서가 뒤집힐 수 없고,
사람이 잊을 단계 자체가 사라진다.

| 바꾼 것 | 전 | 후 |
| --- | --- | --- |
| 스키마 적용 | 사람이 `psql` | 백엔드가 기동 중 |
| 적용 경로 | `docs/schema.sql`(빈 볼륨 전용) + `docs/migrations/*.sql`(수동) | `db/migration/V*.sql` 하나 |
| 로컬 `ddl-auto` | `update` | `validate` |
| compose 의 initdb 마운트 | `docs/schema.sql` | 없음 |
| 헬스체크 `start_period` | 60초 | 120초 |

### 로컬도 validate 로 바꾼 이유

`update` 는 엔티티에 필드를 더하면 로컬에서 조용히 컬럼을 만들어 준다. 개발은 편하지만
마이그레이션 파일을 안 쓴 채 배포되고, 그 사실은 **EC2 에서야** 드러난다.
`validate` 면 같은 실수가 내 컴퓨터에서, 그 자리에서 드러난다.

## 검증 절차

DB 가 어떤 상태였는지가 기동 성패를 가른다. 그래서 상태를 넷으로 나눠 **실제 컨테이너를
띄워** 본다. 임시 DB 를 만들고, 그 DB 를 가리키는 백엔드를 띄우고, 로그에
`Started StudyBackendApplication` 이 뜨는지 본다.

```bash
bash scripts/migration-verify.sh
```

## 결과 — 13건 전부 통과

```
== A. 빈 DB — 처음 올리는 서버 ==
  [PASS] A1 기동 성공
  [PASS] A2 V1 부터 전부 실제로 실행됐다 (=1=SQL,2=SQL,3=SQL,4=SQL)
  [PASS] A3 최신 테이블이 만들어졌다

== B. 구버전 스키마 — 이전 릴리스가 돌던 서버 (실제로 죽었던 경우) ==
  [PASS] B0 이력 표가 없는 상태로 준비됐다
  [PASS] B1 exam_scope 가 아직 없다
  [PASS] B2 기동 성공 — 사람이 psql 을 치지 않았는데 떴다
  [PASS] B3 V1 은 건너뛰고 V2 부터 실행했다 (=1=BASELINE,2=SQL,3=SQL,4=SQL)
  [PASS] B4 exam_scope 가 생겼다
  [PASS] B5 chat_messages 가 생겼다

== C. 최신 스키마 — 손으로 맞춰 둔 서버 ==
  [PASS] C1 기동 성공 — 멱등이라 다시 적용해도 깨지지 않는다
  [PASS] C2 V1 은 건너뛰고 나머지는 멱등하게 다시 돌았다 (=1=BASELINE,2=SQL,3=SQL,4=SQL)

== D. 재기동 — 이미 최신인 DB ==
  [PASS] D1 두 번째 기동도 성공
  [PASS] D2 이력이 늘어나지 않았다 (=1=SQL,2=SQL,3=SQL,4=SQL)

결과: PASS=13 FAIL=0
```

**B 가 이 작업의 이유다.** 예전이라면 죽었을 상태에서, 아무도 psql 을 치지 않았는데 떴다.

### 버전 번호만 보면 안 된다

`1=BASELINE` 과 `1=SQL` 은 다르다. baseline 도 이력에 한 줄로 남기 때문에 버전 번호만
비교하면 **실행된 것과 건너뛴 것이 똑같이 "1" 로 보인다.**

```
BASELINE  건너뛰었다. 이미 그 상태였다고 표시만 한 것
SQL       실제로 실행했다
```

B 에서 V1 이 `BASELINE` 이라는 것이 곧 "V1 을 다시 실행하지 않았다"는 증거다.
실행했다면 `CREATE TABLE`(IF NOT EXISTS 없음)이 이미 있는 테이블에 부딪혀 실패했을 것이다.

## 실제 DB 에 적용한 결과

검증용 임시 DB 말고, 그동안 손으로 마이그레이션을 적용해 온 실제 개발 DB 다.
새 이미지를 올리자 아무 조작 없이 이렇게 됐다.

```
 version |      description      |   type   | success
---------+-----------------------+----------+---------
 1       | << Flyway Baseline >> | BASELINE | t
 2       | general knowledge     | SQL      | t
 3       | study chat            | SQL      | t
 4       | chat message order    | SQL      | t
```

## 검증하다 발견한 버그

전체 테스트에서 챗봇 통합 테스트가 실패했다. **Flyway 와 무관한, 이미 있던 버그**였다.

```java
LocalDateTime now = LocalDateTime.now(clock);
chatMessageRepository.saveAll(List.of(
        ChatMessage.user(session, trimmed, now),      // 같은 시각
        ChatMessage.assistant(session, body, now)));  // 같은 시각
```

조회는 `ORDER BY created_at, id` 였다. 두 줄의 시각이 같으니 정렬에 남는 기준은
**무작위로 만들어진 UUID** 뿐이고, 결과적으로 **답변이 질문 위에 표시될 확률이 절반**이었다.
늘 그런 것도 아니라 더 나빴다 — 앞선 세 번의 테스트 실행에서는 우연히 통과했다.

`message_order` 컬럼을 넣어 세션 안에서 1부터 번호를 매긴다(`Quiz.round` 와 같은 방식).
그 변경이 `V4__chat_message_order.sql` 이고, 이번에 만든 마이그레이션 경로를 처음으로
실제로 태워 본 셈이 됐다.

이미 쌓인 대화에는 번호를 매겨 준다. 시각이 같아 무엇이 먼저였는지 알 수 없는 쌍이 있지만,
한 가지는 확실하다 — **답변은 질문보다 뒤다.**

```sql
row_number() OVER (PARTITION BY session_id ORDER BY created_at ASC, role DESC)
```

## 검증하다 틀린 것 — 스크립트

처음 돌렸을 때 4건이 실패했는데 **전부 스크립트 문제**였다.

| 증상 | 원인 |
| --- | --- |
| 기대 버전이 `1,2,3` | V4 를 만들기 전에 쓴 기댓값 |
| B·C 에서 파일을 못 찾음 | `pwd -W` 와 `pwd` 를 `\|\|` 로 이어 경로가 두 줄이 됨 |

두 번째가 더 위험했다. 씨앗 데이터를 넣지 못했는데도 **B0·B1 이 통과**했다 —
빈 DB 를 "구버전 스키마"라고 부르며 검사하고 있었으니 당연히 통과한 것이다.
아무것도 확인하지 않은 채 초록불이 켜지는 상태였다.

그래서 씨앗 넣기를 `ON_ERROR_STOP=1` 로 바꾸고 실패하면 즉시 멈추게 했다.

`scripts/AGENT.md` 에 적어 둔 "제품 코드를 고치기 전에 스크립트가 틀린 것은 아닌지 먼저
본다"가 그대로 들어맞았다.

## 함께 확인한 것

| 확인 | 결과 |
| --- | --- |
| `./gradlew test` | 904건 통과 |
| `scripts/migration-verify.sh` | 13건 통과 |
| `scripts/chat-verify.sh` | 19건 통과 |
| `scripts/gk-verify.sh` | 19건 통과 |
| 실제 스택 재기동 | 세 컨테이너 healthy |

## 남은 것

- **테스트는 마이그레이션 파일을 검증하지 않는다.** H2 로 돌기 때문에 `DO $$` 블록 같은
  PostgreSQL 문법이 그대로 돌지 않아 Flyway 를 꺼 두었다. 그 자리는
  `scripts/migration-verify.sh` 가 메운다. 마이그레이션을 추가하면 이 스크립트를 돌린다.
- **되돌리는 마이그레이션은 없다.** 컬럼을 지우는 변경이 필요하면 두 번에 나눠 배포한다.
- 인스턴스를 여러 대로 늘려도 Flyway 가 PostgreSQL 자문 잠금을 잡으므로 동시에 떠도
  마이그레이션은 한 번만 돈다. 다만 이 프로젝트는 업로드 파일 때문에 아직 1대 전용이다.
