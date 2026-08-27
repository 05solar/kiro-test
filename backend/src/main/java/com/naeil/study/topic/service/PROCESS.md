# topic/service 작업 절차

## 조회 메서드를 추가할 때

```
1. 세션 조회는 SessionService 를 통한다
2. 클래스는 @Transactional(readOnly = true), 접근시각을 갱신하는 메서드는 @Transactional
3. 빈 결과를 예외로 만들지 않는다
```

`findAll` 이 `@Transactional`(쓰기)인 이유는 `getSessionAndTouch` 가 접근시각을
갱신하기 때문이다. 읽기 전용으로 두면 갱신이 반영되지 않는다.

## STEP 7 에서

커리큘럼 서비스는 이 서비스가 아니라 `TopicRepository` 를 직접 읽어도 된다.
세션 접근시각을 갱신할 필요가 없는 내부 호출이기 때문이다.

## 검증

```bash
./gradlew test --tests "*TopicControllerTest*"
```
