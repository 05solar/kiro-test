# studycontext/entity 작업 절차

## 항목을 추가할 때

```
1. 필드 + @Column(name = "snake_case", columnDefinition = "TEXT")
2. create() / update() 시그니처에 반영
3. isEmpty() 조건에 추가
4. StudyContextRepositoryTest 에 영속화 확인 추가
5. docs/database.md 컬럼 표 갱신
```

## 정규화 규칙을 바꿀 때

```
1. StudyContextPolicy.normalize 수정
2. StudyContextPolicyTest 에 케이스 추가
3. 문서의 정규화 절 갱신
```

가운데 줄바꿈과 공백은 그대로 둔다. 사용자가 목록 형태로 적을 수 있고,
그 구조가 이후 AI 판단에 도움이 된다.

## 검증

```bash
./gradlew test --tests "*StudyContextPolicyTest*" --tests "*StudyContextRepositoryTest*"
```
