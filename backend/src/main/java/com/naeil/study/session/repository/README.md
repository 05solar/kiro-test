# session/repository

세션 영속화.

| 인터페이스 | 역할 |
| --- | --- |
| `StudySessionRepository` | `JpaRepository<StudySession, UUID>` |

## 메서드

```java
Optional<StudySession> findBySessionCode(String sessionCode);
boolean existsBySessionCode(String sessionCode);
```

| 메서드 | 쓰는 곳 |
| --- | --- |
| `findBySessionCode` | 세션 조회 (다른 기기에서 복구) |
| `existsBySessionCode` | 코드 발급 시 중복 검사 |

`session_code` 에 UNIQUE 제약이 있으므로 두 메서드 모두 인덱스를 탄다.
