# topic/entity 작업 절차

## 필드를 추가할 때

```
1. 필드 + @Column(name = "snake_case")
2. Javadoc 에 누가 채우고 언제 바뀌는지 적는다
3. create() 시그니처에 반영 (파라미터가 많아지면 값 객체로 묶는 것을 검토한다)
4. TopicRepositoryTest 에 영속화 확인 추가
5. 실제 PostgreSQL 로 왕복 확인 (특히 JSON 컬럼)
6. docs/database.md 갱신
```

## importance 값을 늘릴 때

Enum에 값을 더하면 DB의 CHECK 제약이 바뀐다. `ddl-auto: update` 는 기존 제약을
고치지 않으므로, 이미 만들어진 DB에서는 제약을 직접 지우고 다시 만들어야 한다.

프롬프트의 importance 설명도 함께 고친다. 코드만 늘리면 AI는 새 값을 쓰지 않는다.

## 검증

```bash
./gradlew test --tests "*TopicRepositoryTest*"
```
