# session/repository 작업 절차

## 조회 메서드를 추가할 때

```
1. 메서드 이름으로 조건이 드러나게 한다 (findByStatusAndExpiresAtBefore)
2. 이름이 길어지면 @Query 를 쓴다. 이름을 억지로 늘리지 않는다
3. 인덱스가 필요한지 확인한다
4. StudySessionRepositoryTest 에 테스트를 추가한다
```

## 만료 세션 삭제 (P1 예정)

```java
List<StudySession> findAllByExpiresAtBefore(LocalDateTime now);
```

배치로 삭제할 때는 연관 데이터와 Storage 파일까지 함께 지워야 한다.
Repository만으로 끝나지 않는 작업이므로 서비스에서 조율한다.

## 검증

```bash
./gradlew test --tests "*StudySessionRepositoryTest*"
```
