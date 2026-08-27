# session/exception — 에이전트 작업 규칙

## 지킬 것

- BusinessException 을 상속하고 ErrorCode 를 넘긴다.
- 메시지는 ErrorCode 의 기본 메시지를 쓴다. 예외마다 문구를 새로 만들지 않는다.
- 진단에 필요한 값(예: attempts)은 필드로 들고 있되 응답에는 넣지 않는다.

## 세션 존재 여부 노출 금지

없는 세션, 만료된 세션, 삭제된 세션 모두 SessionNotFoundException 하나로 응답한다.
만료 안내와 부재 안내를 구분하는 예외를 새로 만들지 않는다.
그 차이가 코드 추측 공격의 신호가 된다.

## 하지 말 것

- @ResponseStatus 를 예외 클래스에 붙이기 (ErrorCode 와 이중 관리)
- 예외 메시지에 세션 코드, UUID, 생성 시각 포함
- checked exception 사용
