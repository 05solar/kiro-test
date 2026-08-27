# studycontext/entity — 에이전트 작업 규칙

## 지킬 것

- 세터를 만들지 않는다. 생성은 `create()`, 변경은 `update()` 로만 한다.
- `update()` 는 전체 교체를 유지한다. null 이 오면 그 항목을 비운다.
- 시각은 파라미터로 받는다. 엔티티 안에서 `LocalDateTime.now()` 를 부르지 않는다.
- `session_id` 는 `updatable = false` 를 유지한다. 소유 세션은 바뀌지 않는다.
- 길이 제한은 `StudyContextPolicy` 에만 둔다.

## 하지 말 것

- 긴 텍스트 컬럼에 `@Lob` 붙이기 (PostgreSQL에서 본문 대신 OID가 저장된다)
- `StudySession` 에 `StudyContext` 필드 추가 (단방향을 유지한다)
- Document 와 연관관계 만들기
- 항목별로 다른 정규화 규칙 적용
