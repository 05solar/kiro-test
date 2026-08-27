# topic/repository — 에이전트 작업 규칙

## 지킬 것

- 조회는 항상 `studySessionId` 조건으로 한다.
- 정렬 없는 목록 조회를 만들지 않는다. Topic은 순서가 의미를 갖는다.
- 재분석은 `deleteAllByStudySessionId` 후 일괄 저장으로 처리한다.

## 하지 말 것

- 세션 범위를 벗어나는 전역 조회
- Topic 개별 갱신 메서드 추가
- 제목이나 요약 내용을 조건으로 하는 검색 쿼리
