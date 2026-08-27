# document/repository — 에이전트 작업 규칙

## 지킬 것

- 문서 조회에는 항상 `studySessionId` 조건을 함께 건다.
- 집계 쿼리는 `coalesce` 로 감싸 `null` 대신 0을 돌려준다.
- 테스트에는 반드시 다른 세션의 데이터를 함께 넣어 격리를 확인한다.

## 하지 말 것

- `findById(documentId)` 만으로 문서를 가져와 서비스에서 소유자 비교
- 세션 범위를 벗어나는 전역 조회 (`findAll`, `findByOriginalFileName` 등)
- 파일 내용을 조건으로 하는 쿼리 (본문은 DB에 없다)
