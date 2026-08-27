# 루트 패키지 작업 절차

## 새 도메인을 추가할 때

```
1. com.naeil.study 아래에 도메인 패키지를 만든다
2. 그 안에 controller / service / repository / entity / dto / exception 을 만든다
3. 각 폴더에 README.md / PROCESS.md / AGENT.md 를 만든다
4. StudySession 과의 연관관계를 정한다 (FK는 UUID id 를 참조)
5. docs/database.md 의 ERD를 갱신한다
```

## 공통 코드를 common 으로 옮길 때

```
1. 두 번째 사용처가 실제로 생겼는지 확인한다
2. 옮긴 뒤 기존 도메인 테스트가 그대로 통과하는지 확인한다
3. common 에는 도메인 지식을 넣지 않는다 (세션 규칙은 session 에 남는다)
```

## 애플리케이션 진입점

`StudyBackendApplication` 은 `@SpringBootApplication` 하나만 갖는다.
빈 정의는 여기 두지 않고 `common/config` 또는 각 도메인 패키지에 둔다.
