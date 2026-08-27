# document/dto — 에이전트 작업 규칙

## 지킬 것

- record 로 만든다. 변환은 정적 팩터리 from() 에 둔다.
- 파일 목록은 항상 배열로 감싼 객체로 응답한다.
  최상위가 배열이면 나중에 필드를 못 늘린다.
- 빈 목록은 null 이 아니라 빈 배열로 응답한다.

## 하지 말 것

- storagePath, storedFileName 노출
- extractedText 전문 노출 (4단계)
- 파일 본문(base64) 포함
- 엔티티를 그대로 반환
