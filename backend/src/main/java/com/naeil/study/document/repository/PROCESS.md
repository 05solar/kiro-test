# document/repository 작업 절차

## 조회 메서드를 추가할 때

```
1. 세션 범위를 벗어나는 조회를 만들지 않는다
   (문서는 항상 세션에 속한 자원이다)
2. 메서드 이름으로 조건이 드러나게 한다
3. 집계가 필요하면 @Query + coalesce 로 null 을 없앤다
4. DocumentRepositoryTest 에 다른 세션 데이터를 섞어 두고 검증한다
```

## STEP 4-2 에서 필요할 것

```java
List<Document> findAllByStudySessionIdAndStatus(UUID sessionId, DocumentStatus status);
```

파싱 대상(`UPLOADED`)만 골라내는 데 쓴다.

## 검증

```bash
./gradlew test --tests "*DocumentRepositoryTest*"
```
