# common/config

애플리케이션 전역 빈 설정.

| 클래스 | 역할 |
| --- | --- |
| `ClockConfig` | `Clock` 빈 등록 |

## Clock 을 빈으로 만든 이유

이 서비스의 핵심 규칙이 시간에 걸려 있다.

```
expiresAt = lastAccessedAt + 30일
```

`LocalDateTime.now()` 를 직접 호출하면 이 규칙을 테스트할 방법이 사라진다.
`Clock` 을 주입받으면 테스트에서 시각을 고정하거나 앞으로 돌릴 수 있다.

```java
Clock fixedClock = Clock.fixed(instant, ZoneId.of("Asia/Seoul"));
```
