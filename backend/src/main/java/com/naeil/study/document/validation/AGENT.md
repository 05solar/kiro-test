# document/validation — 에이전트 작업 규칙

## 지킬 것

- 모든 파일을 검증한 뒤에 저장을 시작하는 구조를 유지한다.
- 확장자를 우선 판단하고 MIME Type은 값이 있을 때만 추가 확인한다.
- 개수와 용량은 기존 저장분과 합쳐서 판단한다.
- 파일명은 DocumentPolicy.normalizeFileName 을 거친 값만 밖으로 내보낸다.

## 하지 말 것

- MIME Type 검증을 엄격하게 바꿔 정상 파일 거부
  (브라우저/OS에 따라 값이 비거나 다르게 온다)
- 검증기 안에서 Storage나 DB 호출
- 파일 내용(매직 넘버) 검사 추가 - 이번 MVP 범위가 아니다
- 제한 값을 이 클래스에 직접 숫자로 쓰기 (DocumentPolicy 에 둔다)
