# document/dto

| 클래스 | 방향 | 쓰이는 곳 |
| --- | --- | --- |
| `DocumentResponse` | 응답 | 파일 한 건 |
| `UploadDocumentsResponse` | 응답 | `POST .../documents` |
| `DocumentListResponse` | 응답 | `GET .../documents` |
| `DocumentParseResponse` | 응답 | `POST .../{documentId}/parse` |
| `ParseDocumentsResponse` | 응답 | `POST .../parse` |

## DocumentResponse

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "originalFileName": "운영체제_1주차.pdf",
  "fileType": "PDF",
  "fileSize": 2481920,
  "status": "UPLOADED",
  "createdAt": "2026-08-27T16:30:00"
}
```

storedFileName 과 storagePath 는 담지 않는다.
사용자에게 필요 없고, 서버 저장 구조를 드러낼 이유도 없다.

## 응답을 둘로 나눈 이유

두 응답의 모양은 같지만 의미가 다르다.

```
UploadDocumentsResponse  이번 요청으로 저장된 파일
DocumentListResponse     세션에 저장된 전체 파일
```

나중에 업로드 응답에만 필요한 정보(예: 남은 용량)가 생겨도 서로 영향을 주지 않는다.
