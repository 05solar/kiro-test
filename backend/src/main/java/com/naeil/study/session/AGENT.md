# session — 에이전트 작업 규칙

## 절대 지킬 것

- `sessionCode` 는 서버에서만 발급한다. 클라이언트가 지정하는 경로를 만들지 않는다.
- 내부 `id`(UUID)를 응답 DTO에 포함하지 않는다.
- 없는 세션과 만료된 세션을 응답에서 구분하지 않는다.
- 세션 코드 형식 검증은 DB 조회 **전에** 한다.
- `availableStudyMinutes` 를 감소시키지 않는다. 감소는 `remainingStudyMinutes` 에서만 일어난다.

## 엔티티

- 세터 금지. 상태 변경은 의도가 드러나는 메서드로 한다 (`touch`, `registerExamInfo`).
- 생성은 `StudySession.create(...)` 정적 팩터리로만.
- 시각은 파라미터로 받는다. 엔티티 안에서 `LocalDateTime.now()` 호출 금지.

## 코드 생성

- `SecureRandom` 을 쓴다. `Random` 이나 `Math.random()` 금지.
- 문자 집합과 길이는 `SessionCodePolicy` 에만 정의한다. 다른 곳에 문자열을 복사하지 않는다.
- 중복 검사는 서비스에서 한다. 생성기는 DB를 모른다.

## 하지 말 것

- 세션 코드로 다른 테이블과 FK 맺기 (UUID `id` 를 쓴다)
- 조회 API에서 `readOnly = true` 인 채로 상태 변경 시도
- 다음 STEP의 필드를 미리 채우는 로직 추가
- 컨트롤러에 코드 생성이나 검증 로직 넣기
