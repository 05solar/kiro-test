# document 작업 절차

## 새 파일 형식을 추가할 때

```
1. DocumentFileType 에 값 추가 (확장자 + MIME Type)
2. DocumentFileTypeTest / DocumentFileValidatorTest 에 케이스 추가
3. docs/api/document-api.md 의 허용 형식 표 갱신
4. 4단계 이후라면 해당 형식의 Parser 도 함께 추가
```

## 업로드 제한을 바꿀 때

```
1. DocumentPolicy 상수 수정
2. application.yml 의 multipart 설정을 같은 값으로 맞춘다
3. DocumentPolicyTest 의 제한 값 검증 수정
4. docs/api/document-api.md 제한 표 갱신
```

두 곳(정책 상수 / multipart 설정)이 어긋나면 컨테이너가 먼저 잘라내면서
검증 메시지가 달라진다.

## STEP 4-2 (텍스트 추출) 예정 작업

```
1. Document 에 extractedText TEXT nullable 추가
2. Document.startParsing() / markParsed(text) / markParseFailed() 도메인 메서드
3. parser 패키지: PdfParser, DocxParser, TxtParser + DocumentParser 인터페이스
4. StorageService.load(storagePath) 로 원본을 읽는다
5. 상태 전이: UPLOADED → PARSING → PARSED / PARSE_FAILED
6. 파싱 실패는 세션 전체 실패로 만들지 않는다 (문서 단위로 표시)
```

## 검증

```bash
./gradlew test --tests "*Document*"
./gradlew test --tests "*LocalStorageServiceTest*"
./gradlew build
```
