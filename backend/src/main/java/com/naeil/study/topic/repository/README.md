# topic/repository

| 인터페이스 | 역할 |
| --- | --- |
| `TopicRepository` | `JpaRepository<Topic, UUID>` |

## 메서드

```java
List<Topic> findAllByStudySessionIdOrderByTopicOrderAsc(UUID sessionId);
void        deleteAllByStudySessionId(UUID sessionId);
```

| 메서드 | 쓰는 곳 |
| --- | --- |
| `findAllBy...OrderByTopicOrderAsc` | 목록 조회, 커리큘럼 생성(STEP 7) |
| `deleteAllByStudySessionId` | 재분석 시 기존 결과 교체 |

## 왜 삭제 후 재삽입인가

분석 결과는 통째로 갈아 끼우는 값이다. 자료나 학습 맥락이 바뀌면 Topic의 개수와
경계 자체가 달라지므로 기존 행과 짝지어 갱신할 방법이 없다.
개별 갱신을 시도하면 어떤 Topic이 어떤 Topic의 후신인지 판단하는 코드가 필요해진다.
