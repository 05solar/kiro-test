# topic/entity — 에이전트 작업 규칙

## 지킬 것

- 세터를 만들지 않는다. 생성은 `create()` 로만 한다.
- 시각은 파라미터로 받는다. 엔티티 안에서 `LocalDateTime.now()` 를 부르지 않는다.
- JSON 컬럼에 `columnDefinition` 을 적지 않는다. Dialect가 고르게 둔다.
- `TopicImportance.from` 의 관대함(공백/대소문자)을 검증기에서 중복하지 않는다.

## 하지 말 것

- `importance` 를 숫자 점수로 바꾸기 (Enum을 유지한다)
- `estimatedStudyMinutes` 에 남은 시간 기반 로직 넣기
- `StudySession` 에 `List<Topic>` 추가 (단방향을 유지한다)
- `@Enumerated(ORDINAL)` 사용
- 엔티티에서 AI 응답 검증 (검증기의 몫이다)
