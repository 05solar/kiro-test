# topic/repository 작업 절차

## 조회 메서드를 추가할 때

```
1. 세션 범위를 벗어나는 조회를 만들지 않는다
2. 정렬 기준을 메서드 이름에 넣는다 (기본은 topicOrder)
3. TopicRepositoryTest 에 다른 세션 데이터를 섞어 두고 검증한다
```

## STEP 7 에서 필요할 것

중요도로 걸러 읽는 메서드가 필요해질 수 있다.

```java
List<Topic> findAllByStudySessionIdAndImportanceIn(UUID sessionId, Collection<TopicImportance> importances);
```

다만 Topic 수가 최대 30개라 전부 읽어 메모리에서 거르는 편이 단순하다.
실제로 필요해질 때 추가한다.

## 검증

```bash
./gradlew test --tests "*TopicRepositoryTest*"
```
