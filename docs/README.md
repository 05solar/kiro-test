# docs

「내일까지 해야 하는데」 프로젝트 문서.

## 문서 목록

| 문서 | 내용 |
| --- | --- |
| [api/](api/) | REST API 명세 |
| [database.md](database.md) | DB 스키마와 ERD |
| [backend-anatomy.html](backend-anatomy.html) | 백엔드 동작 원리 — 도면과 흐름도 (브라우저로 연다) |
| [deployment.html](deployment.html) | AWS 배포 점검표 — 고친 것, 남은 제약, 절차 |
| [frontend-integration-test.md](frontend-integration-test.md) | 프론트 연동 테스트 과정과 결과 |
| [general-knowledge-test.md](general-knowledge-test.md) | 자료 미업로드 시 일반 지식 기반 생성 검증 기록 |
| [study-chat-test.md](study-chat-test.md) | 학습 챗봇 검증 기록 |
| [schema.sql](schema.sql) | 운영 DB 초기 스키마 (ddl-auto=validate 용) |
| [migrations/](migrations/) | 이미 데이터가 있는 DB 에 적용할 스키마 변경분 |
| [live-e2e-report.md](live-e2e-report.md) | 실제 강의자료 라이브 E2E 기록 |

## 문서를 나누는 기준

```
api/          호출하는 쪽(프론트엔드)이 알아야 할 것
database.md   저장 구조. 스키마가 바뀌면 여기부터 고친다
backend-anatomy.html  왜 그렇게 구현했는가. 새로 합류한 사람이 먼저 읽는다
deployment.html       서버에 올릴 때 확인할 것
schema.sql            새 DB 에 처음 적용하는 스키마. pg_dump 로 뽑는다. 손으로 고치지 않는다
migrations/   이미 뜬 DB 에 적용할 변경분. 배포 전에 먼저 적용한다
../PROCESS.md 개발 순서와 검증 기록
../AGENT.md   에이전트 작업 규칙
```

기획 원문(전체 MVP 명세)은 별도 문서로 관리되며, 이 폴더의 문서는 **실제 구현된 내용**만 담는다.
아직 만들지 않은 기능은 "예정"으로 표시하고 상세 명세를 쓰지 않는다.
