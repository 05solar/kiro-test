# docs/migrations

**이 폴더는 더 이상 쓰지 않는다.** 마이그레이션은 애플리케이션이 스스로 적용한다.

```
backend/src/main/resources/db/migration/
├── V1__initial_schema.sql       최초 스키마
├── V2__general_knowledge.sql    exam_scope, source_type   (구 001-general-knowledge.sql)
└── V3__study_chat.sql           chat_messages             (구 002-study-chat.sql)
```

여기 남은 `001-*.sql`, `002-*.sql` 은 **기록**이다. 새로 적용할 일이 없다.
내용은 각각 V2, V3 으로 그대로 옮겼다.

## 왜 옮겼나

예전 방식은 이랬다.

```
1. psql 로 마이그레이션 적용        ← 사람이
2. 새 이미지 배포                   ← 사람이
```

두 단계의 **순서가 배포 밖에 있었다.** 2번을 먼저 하면 컬럼이 없는 채로 기동해
`Schema-validation: missing column` 으로 죽는다. 실제로 겪었다.

```
SchemaManagementException: Schema-validation: missing column [exam_scope] in table [study_sessions]
dependency failed to start: container naeil-backend-1 is unhealthy
```

Flyway 는 Hibernate 검증보다 먼저, **같은 기동 안에서** 돌기 때문에 이 순서가 뒤집힐 수 없다.
사람이 잊을 단계 자체가 사라진다.

## 새로 마이그레이션을 쓸 때

`backend/src/main/resources/db/migration/` 에 다음 번호로 만든다.
규칙과 확인 방법은 [DEPLOY.md](../../DEPLOY.md#스키마를-바꿨을-때) 에 있다.

요점만 옮기면,

- 이미 적용된 파일은 고치지 않는다 (체크섬이 어긋나면 기동이 멈춘다)
- 멱등하게 쓴다 (`IF NOT EXISTS`) — baseline 된 DB 에서는 V2 부터 다시 실행된다
- 컬럼을 지우는 변경은 두 번에 나눠 배포한다

## 이 폴더를 지우지 않은 이유

이미 이 파일들을 손으로 적용해 둔 환경이 있고, 그 환경에서 "내가 뭘 적용했더라"를
확인할 곳이 필요하다. 지우면 그 흔적이 사라진다.
