# session/service 작업 절차

## 유스케이스를 추가할 때

```
1. 도메인 메서드가 엔티티에 있는지 먼저 본다 (상태 변경은 엔티티가 한다)
2. 서비스 메서드는 조율만 한다: 검증 → 조회 → 도메인 메서드 호출
3. 쓰기 메서드에 @Transactional 을 붙인다 (클래스는 readOnly = true)
4. 실패 경로를 먼저 정한다 (어떤 예외가 언제 나가는가)
5. 테스트: 정상 경로 1개 + 실패 경로 전부
```

## 테스트 작성 방식

SessionServiceTest 는 Spring 컨텍스트 없이 순수 Mockito로 돌린다.

```java
Clock fixedClock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
sessionService = new SessionService(repository, generator, fixedClock, 30L, 10);
```

시간을 고정하므로 expiresAt 이 정확히 30일 뒤인지 단언할 수 있다.
느슨한 isAfter 대신 isEqualTo(NOW.plusDays(30)) 을 쓴다.

## 검증

```bash
./gradlew test --tests "*SessionServiceTest*" --tests "*SessionCodeGeneratorTest*"
```
