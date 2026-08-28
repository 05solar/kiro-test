# docs/migrations

이미 데이터가 있는 DB 에 적용할 스키마 변경분.

`docs/schema.sql` 은 **새 DB 에 처음 적용하는** 전체 스키마다(볼륨이 비어 있을 때만
자동 적용된다). 이미 뜬 DB 에는 아무 영향이 없으므로, 컬럼이 늘거나 제약이 바뀌면
그 변경분을 여기에 순번을 붙여 남긴다.

| 파일 | 내용 |
| --- | --- |
| `001-general-knowledge.sql` | `study_sessions.exam_scope`, `study_sessions.source_type` 추가 |

## 순서가 중요하다

운영 프로파일은 `ddl-auto=validate` 다. 새 이미지를 먼저 올리면 컬럼이 없어서
기동 중 죽는다. 실제로 그렇게 실패했다.

```
SchemaManagementException: Schema-validation: missing column [exam_scope] in table [study_sessions]
```

**마이그레이션을 먼저, 배포를 나중에.**

```bash
docker compose exec -T db psql -U postgres -d naeil_study < docs/migrations/001-general-knowledge.sql
docker compose up -d --build
```

## 작성 규칙

- `ADD COLUMN IF NOT EXISTS` 처럼 **여러 번 돌려도 같은 결과**가 되게 쓴다.
  적용 이력을 기록하는 도구가 아직 없어서, 어디까지 적용했는지 확실하지 않을 때가 있다.
- 기존 행을 어떻게 채울지 같이 적는다. 새 컬럼을 NULL 로 두는 것이 맞는 경우
  (예: "아직 분석하지 않음")와 값을 채워야 하는 경우를 구분한다.
- 왜 이 컬럼이 필요한지 주석으로 남긴다. SQL 만 보면 나중에 지워도 되는지 알 수 없다.

마이그레이션 도구(Flyway)는 아직 도입하지 않았다. 파일 수가 늘어나면 그때 옮긴다.
