# studycontext/dto 작업 절차

## 항목을 추가할 때

```
1. UpdateStudyContextRequest 에 필드 + @Size
2. StudyContextResponse 에 필드 + from() / empty() 반영
3. StudyContextControllerTest 에 JSON 키 검증 추가
4. StudyContextApiIntegrationTest 에 실제 응답 확인 추가
5. docs/api/study-context-api.md 예시 JSON 갱신
```

`empty()` 를 빠뜨리면 미입력 조회 응답에서 그 항목만 사라진다.

## 검증 메시지를 쓸 때

메시지가 곧 사용자 안내다. 어느 항목이 문제인지 알 수 있게 쓴다.

```
"교수님 강조 내용은 2000자 이하로 입력해 주세요."
```

`GlobalExceptionHandler` 가 첫 번째 필드 오류 메시지를 응답에 담는다.

## 검증

```bash
./gradlew test --tests "*StudyContextControllerTest*" --tests "*StudyContextApiIntegrationTest*"
```
