# document/parser 작업 절차

## 새 형식의 파서를 추가할 때

```
1. DocumentFileType 에 형식 추가 (확장자 + MIME Type)
2. DocumentParser 구현체 작성
   - supports() 로 담당 형식 선언
   - 실패는 DocumentParseFailedException 으로 감싼다
   - 스트림은 try-with-resources 로 닫는다
3. @Component 등록만 하면 팩터리가 자동으로 인식한다
4. 파서 단위 테스트 작성 (정상 / 한글 / 빈 파일 / 손상 파일)
5. docs/api/document-parsing-api.md 형식 표 갱신
```

## 테스트 파일 준비 방식

바이너리 픽스처를 저장소에 넣지 않는다. 테스트에서 PDFBox/POI로 직접 만든다.

```java
try (PDDocument document = new PDDocument()) { ... document.save(out); }
try (XWPFDocument document = new XWPFDocument()) { ... document.write(out); }
```

한글 PDF는 한글 폰트를 임베딩해야 만들 수 있어서, 시스템 폰트가 있을 때만 검증하고
없으면 `Assumptions.assumeTrue` 로 건너뛴다. 한글 추출 자체는 DOCX와 TXT로도 검증한다.

## 정규화 규칙을 바꿀 때

```
1. 구조 정보를 잃는 처리인지 먼저 판단한다
   (줄바꿈, 들여쓰기, 표의 탭은 구조 정보다)
2. TextNormalizerTest 에 케이스를 추가한다
3. docs/api/document-parsing-api.md 의 정규화 표를 갱신한다
```

## 검증

```bash
./gradlew test --tests "*ParserTest*" --tests "*TextNormalizerTest*"
```
