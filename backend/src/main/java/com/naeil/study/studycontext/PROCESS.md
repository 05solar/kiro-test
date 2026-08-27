# studycontext 작업 절차

## 항목을 추가할 때

```
1. StudyContext 엔티티에 필드 + @Column(columnDefinition = "TEXT")
2. update() / create() 시그니처에 반영 (전체 교체 성격을 유지한다)
3. UpdateStudyContextRequest 에 @Size(max = MAX_FIELD_LENGTH) 와 함께 추가
4. StudyContextResponse 에 추가
5. 서비스에서 StudyContextPolicy.normalize 를 거쳐 저장
6. 테스트: 정규화 / 저장 / 수정 / 조회
7. docs/api/study-context-api.md 와 docs/database.md 갱신
```

항목이 늘어나면 `update()` 파라미터도 늘어난다. 다섯 개를 넘어가면
전용 값 객체로 묶는 것을 검토한다. 지금(네 개)은 그대로 둔다.

## 길이 제한을 바꿀 때

```
1. StudyContextPolicy.MAX_FIELD_LENGTH 수정
2. StudyContextPolicyTest 의 상수 검증 수정
3. DTO 의 메시지 문구 수정
4. 문서의 제한 표 갱신
```

`@Size` 는 정규화 **전** 원본 길이를 본다. 앞뒤 공백까지 길이에 포함된다.

## STEP 6 (AI 분석) 에서 쓰는 방법

```java
Optional<StudyContext> context = studyContextRepository.findByStudySessionId(sessionId);
```

없을 수 있다. AI 요청을 만들 때 `Optional.isEmpty()` 또는 `StudyContext.isEmpty()` 로
맥락 절을 통째로 뺄지 판단한다.

## 검증

```bash
./gradlew test --tests "*StudyContext*"
./gradlew build
```
