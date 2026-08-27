# studycontext/service 작업 절차

## 유스케이스를 추가할 때

```
1. 세션 조회는 SessionService 를 통한다
2. 쓰기 메서드에 @Transactional (클래스는 readOnly = true)
3. 입력값은 StudyContextPolicy 를 거친다
4. 테스트: 생성 / 수정 / 전부 null / 정규화 / 세션 없음
```

## 테스트 작성 방식

Spring 컨텍스트 없이 순수 Mockito로 돌린다. 시각은 고정한다.

```java
Clock fixedClock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
studyContextService = new StudyContextService(repository, sessionService, fixedClock);
```

수정 경로에서는 `verify(repository, never()).save(...)` 로 새 행이 생기지 않았음을 확인한다.

## STEP 6 (AI 분석) 에서 쓰는 방법

AI 분석 서비스는 이 서비스가 아니라 리포지터리를 직접 읽어도 된다.
세션 접근시각을 갱신할 필요가 없는 내부 호출이기 때문이다.

```java
Optional<StudyContext> context = studyContextRepository.findByStudySessionId(sessionId);
```

## 검증

```bash
./gradlew test --tests "*StudyContextServiceTest*"
```
