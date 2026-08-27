# common/config — 에이전트 작업 규칙

## 지킬 것

- 설정 값은 `application.yml` 에서 환경변수로 주입받는다. 코드에 상수로 박지 않는다.
- 빈 하나가 무엇을 위해 존재하는지 Javadoc으로 남긴다.

## 하지 말 것

- 여기에 비즈니스 로직 넣기
- `Clock` 빈을 제거하고 `LocalDateTime.now()` 로 되돌리기
- DB 접속 정보를 코드에 하드코딩
