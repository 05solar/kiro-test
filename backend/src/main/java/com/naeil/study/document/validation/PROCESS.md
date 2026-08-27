# document/validation 작업 절차

## 검증 규칙을 추가할 때

```
1. 제한 값은 DocumentPolicy 에 상수로 둔다
2. 실패는 전용 예외로 던진다 (에러 코드가 곧 사용자 안내다)
3. 저장 전에 판단할 수 있는 규칙인지 확인한다
   저장 후에만 알 수 있는 것(파일 내용 검사)은 여기 두지 않는다
4. DocumentFileValidatorTest 에 경계값 테스트를 추가한다
   (정확히 한도 = 허용, 한도 + 1 = 거부)
```

## 테스트 작성 방식

MockMultipartFile 로 파일을 만든다. 실제 파일 시스템을 쓰지 않는다.

```java
new MockMultipartFile("files", "운영체제.pdf", "application/pdf", content);
```

## 검증

```bash
./gradlew test --tests "*DocumentFileValidatorTest*"
```
