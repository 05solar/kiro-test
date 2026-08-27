# document/controller 작업 절차

## 엔드포인트를 추가할 때

```
1. 경로가 세션 아래에 있는지 확인한다
2. 매핑과 DTO 변환만 작성한다
3. 상태 코드를 명시한다 (생성 201, 조회 200, 삭제 204)
4. @WebMvcTest 로 슬라이스 테스트 작성
5. 통합 테스트에 실제 왕복 추가
6. docs/api/document-api.md 갱신
```

## multipart 테스트 방식

슬라이스 테스트:

```java
mockMvc.perform(multipart("/api/sessions/{code}/documents", code)
        .file(new MockMultipartFile("files", "자료.pdf", "application/pdf", bytes)))
```

통합 테스트는 MultiValueMap + ByteArrayResource 를 쓴다.
파일명을 넘기려면 getFilename() 을 덮어써야 한다.

검증 항목:

```
POST 정상                    201
POST 지원하지 않는 파일        400
POST 개수 초과                400
POST files 파트 없음          400
GET                          200
DELETE                       204
DELETE 다른 세션 문서          404
DELETE documentId 형식 오류    400
```

## 검증

```bash
./gradlew test --tests "*DocumentControllerTest*"
```
