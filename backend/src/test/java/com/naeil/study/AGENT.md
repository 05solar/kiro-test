# 테스트 — 에이전트 작업 규칙

## 지킬 것

- @DisplayName 을 한국어로 붙인다. 테스트 목록이 그대로 기능 명세가 되게 한다.
- 하나의 테스트는 하나를 검증한다. 이름에 그 하나가 드러나야 한다.
- 시간은 Clock 으로 고정한다. 통합 테스트에서는 MutableClock 으로 앞으로 돌린다.
- 값을 정확히 단언한다. isEqualTo(NOW.plusDays(30)) 을 쓰고 isNotNull() 로 얼버무리지 않는다.
- 실패 경로를 반드시 덮는다. 정상 경로만 있는 테스트는 절반만 한 것이다.

## 외부 의존 금지

테스트는 PostgreSQL, Docker, 네트워크 없이 통과해야 한다.
Testcontainers 를 도입하려면 그 전에 CI 환경을 먼저 정한다.

## 하지 말 것

- Thread.sleep 으로 시간 흐름 흉내내기
- @MockBean 사용 (deprecated. @MockitoBean 을 쓴다)
- 테스트 간 실행 순서에 의존하기
- 실제 LLM API 호출. `FakeAiAnalysisClient` 를 쓴다 (과금 + 응답 흔들림 + 네트워크 의존)
- 바이너리 픽스처를 저장소에 커밋 (PDF/DOCX는 테스트에서 라이브러리로 생성한다)
- 단언 없는 테스트 (호출만 하고 통과시키기)
