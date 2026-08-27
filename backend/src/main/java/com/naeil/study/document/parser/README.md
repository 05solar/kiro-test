# document/parser

파일 형식별 텍스트 추출기와 정규화기.

| 클래스 | 역할 |
| --- | --- |
| `DocumentParser` | 파서 인터페이스 |
| `PdfDocumentParser` | PDF (Apache PDFBox) |
| `DocxDocumentParser` | DOCX (Apache POI) |
| `TxtDocumentParser` | TXT (JDK) |
| `DocumentParserFactory` | 형식 → 파서 선택 |
| `ParsedDocument` | 추출 결과 |
| `TextNormalizer` | 추출 텍스트 정리 |

## 파서의 책임

```
InputStream → 텍스트
```

여기까지다. 정규화, 상태 변경, DB 저장은 하지 않는다.
추출에 실패하면 `DocumentParseFailedException` 을 던진다. 라이브러리 예외를 그대로 올리지 않는다.

## 파서 선택

`DocumentParserFactory` 가 스프링이 주입한 파서 목록을 형식별로 색인한다.
새 형식을 추가할 때 팩터리는 고치지 않는다. `DocumentParser` 구현체만 만들면 된다.

`if (type == PDF) ... else if (type == DOCX)` 같은 분기를 여러 곳에 두지 않는다.

기동 시점에 모든 `DocumentFileType` 에 파서가 있는지 확인한다.
업로드는 허용하는데 파서가 없으면 런타임에야 드러나기 때문이다.

## 형식별 처리

### PDF

텍스트 레이어가 있는 PDF만 지원한다. 스캔본은 빈 문자열이 나오고 상위에서
`NO_EXTRACTABLE_TEXT` 로 처리한다. OCR은 MVP 범위가 아니다.
암호가 걸린 PDF는 파싱 실패로 처리한다.

### DOCX

본문 문단과 **표 안의 텍스트를 모두** 뽑는다. 강의자료는 표에 핵심 정리가 들어 있는 경우가
많아 표를 빠뜨리면 학습에 필요한 내용이 통째로 사라진다.
본문 요소를 순서대로 훑으므로 문단과 표의 등장 순서가 유지된다.

### TXT

UTF-8 엄격 디코딩을 먼저 시도하고, 실패하면 MS949로 한 번 더 시도한다.
한국어 TXT는 메모장에서 CP949로 저장되는 경우가 흔하다.
`new String(bytes, UTF_8)` 은 잘못된 바이트를 물음표로 바꿔 버려 인코딩이 틀렸다는 사실
자체를 알 수 없다. 그래서 디코더를 직접 쓴다.

## TextNormalizer

구조는 남기고 잡음만 없앤다.

```
하는 것    CRLF 통일, 줄 끝 공백 제거, 빈 줄 3개 이상 → 1개,
           U+00A0 → 공백, 폭 없는 문자 제거
하지 않는 것  줄바꿈 제거, 줄 안 연속 공백 축약, 들여쓰기 제거
```

줄바꿈과 들여쓰기를 남기는 이유는 이후 AI가 제목, 소제목, 목록, 문단, 표를 구분해야 하기
때문이다. 한 줄로 뭉개면 문서의 구조 정보가 사라진다.
