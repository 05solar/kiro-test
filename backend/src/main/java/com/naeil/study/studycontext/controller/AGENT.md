# studycontext/controller — 에이전트 작업 규칙

## 지킬 것

- 컨트롤러는 얇게 유지한다. 매핑과 변환만 한다.
- `@Valid` 를 붙여 길이 검증이 서비스에 닿기 전에 끝나게 한다.
- 미입력 조회는 `StudyContextResponse.empty(sessionCode)` 로 200 응답한다.
- 모든 경로를 `/api/sessions/{sessionCode}` 아래에 둔다.

## 하지 말 것

- DELETE 엔드포인트 추가
- 미입력 조회에 404 응답
- PATCH 로 바꿔 부분 수정 의미 부여
- 컨트롤러에서 정규화 수행
- 세션 없이 학습 맥락에 접근하는 경로 추가
