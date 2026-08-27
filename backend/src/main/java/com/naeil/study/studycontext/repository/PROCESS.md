# studycontext/repository 작업 절차

## 조회 메서드를 추가할 때

```
1. 세션 범위를 벗어나는 조회를 만들지 않는다
2. 실제 호출하는 곳이 생길 때 추가한다
3. StudyContextRepositoryTest 에 다른 세션 데이터를 섞어 두고 검증한다
```

## STEP 6 에서

AI 분석 서비스가 세션 ID로 학습 맥락을 읽는다. 지금 메서드로 충분하다.

```java
Optional<StudyContext> context = studyContextRepository.findByStudySessionId(sessionId);
```

## 검증

```bash
./gradlew test --tests "*StudyContextRepositoryTest*"
```
