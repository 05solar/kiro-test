# session/service — 에이전트 작업 규칙

## 지킬 것

- 생성자 주입만 쓴다. @RequiredArgsConstructor 도 쓰지 않는다.
  의존성이 늘어나는 것이 생성자에서 바로 보여야 한다.
- 클래스에 @Transactional(readOnly = true), 쓰기 메서드에만 @Transactional.
- 시각은 주입받은 Clock 에서만 얻는다.
- 설정값(expiration-days, max-generation-attempts)은 @Value 로 받고 기본값을 준다.
- 상태를 바꾸는 메서드는 이름에 그 사실을 드러낸다.

## 코드 발급

- 중복 검사 없이 저장하지 않는다.
- 재시도 횟수에 상한을 둔다. 무한 루프를 만들지 않는다.
- 상한에 도달하면 조용히 넘어가지 않고 예외를 던진다.

## 하지 말 것

- 엔티티 대신 서비스에서 필드를 직접 조작
- 컨트롤러에 검증 로직 넘기기 (형식 검증은 서비스 진입 지점에서 한다)
- SecureRandom 을 Random 으로 교체
- 트랜잭션 안에서 외부 API 호출 (STEP 5 AI 분석에서 특히 주의)
