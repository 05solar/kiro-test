# session/entity 작업 절차

## 필드를 추가할 때

```
1. 필드 + @Column(name = "snake_case")
2. Javadoc 에 "언제 채워지는지, 누가 바꾸는지" 를 적는다
3. 값을 바꾸는 도메인 메서드를 함께 만든다
4. StudySessionRepositoryTest 에 영속화 확인을 추가한다
5. docs/database.md 컬럼 표 갱신
```

## 상태 전이를 추가할 때

```
1. SessionStatus 에 값이 이미 있는지 확인 (7개가 미리 정의되어 있다)
2. 전이 메서드를 엔티티에 추가 (예: startUploading())
3. 잘못된 전이는 예외로 막는다
4. 테스트: 정상 전이 + 잘못된 전이
```

## 연관관계를 추가할 때 (STEP 4 이후)

```
1. FK 는 UUID id 를 참조한다. sessionCode 를 FK 로 쓰지 않는다
2. 세션 삭제 시 함께 지워져야 하므로 cascade / orphanRemoval 을 검토한다
3. 양방향이 꼭 필요한지 확인한다. 단방향으로 충분하면 단방향으로 둔다
4. N+1 문제를 만들지 않는지 확인한다
```

## 검증

```bash
./gradlew test --tests "*RepositoryTest*" --tests "*PolicyTest*"
```
