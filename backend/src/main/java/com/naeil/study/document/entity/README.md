# document/entity

| 클래스 | 역할 |
| --- | --- |
| `Document` | 업로드된 파일 한 건의 메타데이터 |
| `DocumentFileType` | 허용 형식 (PDF / DOCX / TXT) + MIME Type |
| `DocumentStatus` | 텍스트 추출 상태 |
| `DocumentPolicy` | 업로드 제한과 파일명 처리 규칙 |

## Document

```
id                UUID
studySession      @ManyToOne(LAZY), FK session_id  (단방향)
originalFileName  사용자가 올린 이름. 표시용
storedFileName    Storage 실제 이름 (UUID + 확장자)
storagePath       Storage root 기준 상대 경로
fileType          PDF / DOCX / TXT
fileSize          byte
status            UPLOADED (3단계에서 만들어지는 유일한 값)
createdAt / updatedAt
```

| 메서드 | 설명 |
| --- | --- |
| `create(session, name, storedFile, type, size, now)` | 생성. 상태는 `UPLOADED` |
| `belongsTo(sessionId)` | 소유 세션 확인 |

세터는 없다. 파일 본문은 담지 않는다.

## DocumentFileType

확장자와 MIME Type을 함께 들고 있다.

| 형식 | 확장자 | MIME Type |
| --- | --- | --- |
| PDF | `pdf` | `application/pdf` |
| DOCX | `docx` | `...wordprocessingml.document` 외 |
| TXT | `txt` | `text/plain` |

`matchesContentType()` 은 MIME Type이 비어 있거나 `application/octet-stream` 이면
판단을 보류하고 통과시킨다. 브라우저/OS마다 값이 달라 정상 파일이 거부되는 편을 피했다.

## DocumentPolicy

```
MAX_FILE_COUNT        10
MAX_FILE_SIZE_BYTES   20MB
MAX_TOTAL_SIZE_BYTES  100MB
```

`normalizeFileName()` 은 경로 구분자 뒤 마지막 조각만 남기고 제어문자를 제거한다.

```
../../../etc/passwd.pdf  →  passwd.pdf
C:\Users\me\운영체제.pdf  →  운영체제.pdf
..                       →  (빈 문자열, 거부)
```
