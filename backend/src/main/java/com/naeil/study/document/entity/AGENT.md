# document/entity — 에이전트 작업 규칙

## 지킬 것

- 세터를 만들지 않는다. 생성은 `Document.create(...)` 정적 팩터리로만 한다.
- 시각은 파라미터로 받는다. 엔티티 안에서 `LocalDateTime.now()` 를 부르지 않는다.
- `storedFileName`, `storagePath` 는 `updatable = false` 를 유지한다. 저장 후 바뀌지 않는다.
- 제한 값과 파일명 규칙은 `DocumentPolicy` 에만 둔다. 다른 곳에 숫자를 복사하지 않는다.
- 연관관계는 `@ManyToOne(LAZY)` 단방향을 유지한다.

## 하지 말 것

- `StudySession` 에 `List<Document>` 추가 (양방향으로 만들지 않는다)
- 파일 본문(byte[])을 엔티티에 넣기
- `@Enumerated(ORDINAL)` 사용
- MIME Type 검증을 엄격하게 바꿔 정상 파일을 거부하기
