# topic 작업 절차

## 필드를 추가할 때

```
1. Topic 엔티티에 필드 + Javadoc (누가 채우고 언제 바뀌는지)
2. create() 시그니처에 반영
3. analysis/validation/ValidatedTopic 과 검증기에 반영
4. analysis/client/dto/AiTopicResult 와 프롬프트에 반영 (AI가 채우는 값이면)
5. TopicResponse 에 노출할지 판단
6. TopicRepositoryTest 에 영속화 확인 추가
7. docs/database.md 와 docs/api/analysis-api.md 갱신
```

AI가 채우는 값은 반드시 검증기를 거친다. 응답을 그대로 저장하지 않는다.

## JSON 컬럼을 다룰 때

`keyPoints` 와 `sourceDocumentIds` 는 `@JdbcTypeCode(SqlTypes.JSON)` 을 쓴다.

```
columnDefinition 을 적지 않는다.
PostgreSQL은 jsonb, H2는 json 으로 Dialect가 고른다.
직접 적으면 한쪽 DB에서 DDL이 깨진다.
```

새 JSON 컬럼을 추가하면 실제 PostgreSQL로 왕복을 확인한다. H2에서만 통과하는 매핑이 있다.

## STEP 7 (커리큘럼) 에서 쓰는 방법

```java
List<Topic> topics = topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(sessionId);
```

`importance`, `estimatedStudyMinutes`, 4개 matched 값으로 남은 시간에 맞는 계획을 만든다.
`mustStudyMatched` 인 Topic은 시간이 부족해도 빼지 않는다.

## 검증

```bash
./gradlew test --tests "*Topic*"
```
