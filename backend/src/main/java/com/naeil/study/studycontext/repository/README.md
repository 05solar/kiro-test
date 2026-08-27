# studycontext/repository

| 인터페이스 | 역할 |
| --- | --- |
| `StudyContextRepository` | `JpaRepository<StudyContext, UUID>` |

## 메서드

```java
Optional<StudyContext> findByStudySessionId(UUID sessionId);
```

세션당 최대 하나이므로 `Optional` 을 돌려준다.
조회 조건은 항상 세션 ID다. 다른 세션의 맥락이 넘어올 경로를 만들지 않는다.

`existsByStudySessionId` 는 만들지 않았다. Upsert 흐름에서 조회한 결과를 그대로 쓰므로
존재 확인만 따로 할 일이 없다. 쓰지 않는 메서드를 미리 두지 않는다.
