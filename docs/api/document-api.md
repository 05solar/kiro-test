# 강의자료 API 명세

세션에 강의자료를 업로드하고 목록을 보거나 삭제하는 API.

이번 단계에서는 파일을 **저장만** 한다. PDF/DOCX/TXT 내부 텍스트는 읽지 않는다.
텍스트 추출은 4단계에서 구현한다.

- Base URL: `/api/sessions/{sessionCode}/documents`
- 인증: 없음. 8자리 세션 코드가 접근 키다.
- 모든 경로가 세션 아래에 있다. 문서 ID만으로 접근하는 경로는 없다.

## 목차

- [POST — 업로드](#post-apisessionssessioncodedocuments--업로드)
- [GET — 목록 조회](#get-apisessionssessioncodedocuments--목록-조회)
- [DELETE — 삭제](#delete-apisessionssessioncodedocumentsdocumentid--삭제)
- [업로드 제한](#업로드-제한)
- [파일 형식 판별](#파일-형식-판별)
- [파일명 처리](#파일명-처리)
- [저장 구조](#저장-구조)
- [상태 값](#상태-값)

---

## POST /api/sessions/{sessionCode}/documents — 업로드

### 요청

```http
POST /api/sessions/7K2M9QXF/documents
Content-Type: multipart/form-data
```

| 파트 | 필수 | 설명 |
| --- | --- | --- |
| `files` | 예 | 업로드할 파일. 같은 이름으로 여러 개 보낼 수 있다 |

### 원자성

한 요청은 **전체 성공하거나 전체 실패한다.**

```
file1.pdf  정상
file2.docx 정상
file3.exe  비정상
        ↓
전체 요청 실패. 아무 파일도 저장되지 않는다
```

모든 파일을 먼저 검증한 뒤 저장을 시작하고, 저장 도중 실패하면 이미 저장한 파일을 지운다.

### 부수 효과

```
세션 상태  CREATED → UPLOADING   (최초 업로드 성공 시)
lastAccessedAt = now
expiresAt      = now + 30일
```

세션이 이미 `UPLOADING` 이후 단계라면 상태를 되돌리지 않는다.

### 응답 — 201 Created

```json
{
  "documents": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "originalFileName": "운영체제_1주차.pdf",
      "fileType": "PDF",
      "fileSize": 2481920,
      "status": "UPLOADED",
      "createdAt": "2026-08-27T16:30:00"
    },
    {
      "id": "a6fa6d6e-1111-4000-8000-000000000002",
      "originalFileName": "운영체제_정리.docx",
      "fileType": "DOCX",
      "fileSize": 481204,
      "status": "UPLOADED",
      "createdAt": "2026-08-27T16:30:00"
    }
  ]
}
```

이번 요청으로 저장된 파일만 담는다. 세션 전체 목록은 GET으로 조회한다.

`storedFileName`과 `storagePath`는 응답에 넣지 않는다. 사용자에게 필요 없고,
서버 저장 구조를 드러낼 이유도 없다.

### 오류

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_SESSION_CODE` | 세션 코드 형식 오류 |
| 400 | `INVALID_REQUEST` | `files` 파트가 없음 |
| 400 | `EMPTY_FILE` | 내용이 없는 파일이 포함됨 |
| 400 | `UNSUPPORTED_FILE_TYPE` | PDF/DOCX/TXT가 아닌 파일이 포함됨 |
| 400 | `FILE_SIZE_EXCEEDED` | 20MB를 넘는 파일이 포함됨 |
| 400 | `FILE_COUNT_EXCEEDED` | 세션 파일 개수가 10개를 넘음 |
| 400 | `SESSION_STORAGE_EXCEEDED` | 세션 전체 용량이 100MB를 넘음 |
| 404 | `SESSION_NOT_FOUND` | 해당 코드의 세션이 없음 |
| 500 | `FILE_STORAGE_FAILED` | 파일 저장소 오류 |

---

## GET /api/sessions/{sessionCode}/documents — 목록 조회

### 요청

```http
GET /api/sessions/7K2M9QXF/documents
```

### 응답 — 200 OK

업로드한 순서대로 정렬된다.

```json
{
  "documents": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "originalFileName": "운영체제_1주차.pdf",
      "fileType": "PDF",
      "fileSize": 2481920,
      "status": "UPLOADED",
      "createdAt": "2026-08-27T16:30:00"
    }
  ]
}
```

업로드한 파일이 없으면 빈 배열이 나간다.

```json
{ "documents": [] }
```

다른 세션의 문서는 포함되지 않는다.

### 오류

| 상태 | code |
| --- | --- |
| 400 | `INVALID_SESSION_CODE` |
| 404 | `SESSION_NOT_FOUND` |

---

## DELETE /api/sessions/{sessionCode}/documents/{documentId} — 삭제

AI 분석을 시작하기 전 잘못 올린 자료를 지울 수 있다.

### 요청

```http
DELETE /api/sessions/7K2M9QXF/documents/550e8400-e29b-41d4-a716-446655440000
```

### 응답 — 204 No Content

본문 없음.

### 다른 세션의 문서는 지울 수 없다

문서 ID와 세션 ID를 **함께 조건으로** 조회하므로, 다른 세션의 문서는 애초에 찾히지 않는다.

```
DELETE /api/sessions/{공격자세션}/documents/{남의문서ID}
        ↓
404 DOCUMENT_NOT_FOUND
```

403이 아니라 404를 쓴다. 403은 "그 ID의 문서가 어딘가에 존재한다"는 사실을 알려준다.

### 오류

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_SESSION_CODE` | 세션 코드 형식 오류 |
| 400 | `INVALID_REQUEST` | `documentId`가 UUID 형식이 아님 |
| 404 | `SESSION_NOT_FOUND` | 해당 코드의 세션이 없음 |
| 404 | `DOCUMENT_NOT_FOUND` | 해당 세션에 그 문서가 없음 (다른 세션 소유 포함) |
| 500 | `FILE_STORAGE_FAILED` | 파일 저장소 오류 |

---

## 업로드 제한

| 항목 | 제한 |
| --- | --- |
| 한 요청 파일 개수 | 10개 |
| 세션 전체 파일 개수 | 10개 |
| 개별 파일 크기 | 20MB |
| 세션 전체 용량 | 100MB |
| 허용 형식 | PDF, DOCX, TXT |

개수와 용량은 **이미 저장된 것과 합쳐서** 판단한다.

```
현재 7개 + 새 업로드 5개 = 12개 → 거부
```

서블릿 컨테이너의 multipart 제한(`application.yml`)도 같은 값으로 맞춰 두었다.
컨테이너가 먼저 잘라낸 경우에도 `FILE_SIZE_EXCEEDED`로 응답한다.

---

## 파일 형식 판별

확장자를 먼저 보고, MIME Type은 값이 있을 때만 추가로 확인한다.

| 형식 | 확장자 | MIME Type |
| --- | --- | --- |
| PDF | `.pdf` | `application/pdf` |
| DOCX | `.docx` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| TXT | `.txt` | `text/plain` |

MIME Type이 비어 있거나 `application/octet-stream` 처럼 판단할 수 없는 값이면
확장자만으로 결정한다. 브라우저나 OS에 따라 MIME Type이 다르게 오는 경우가 있어서,
정상 파일이 거부되는 쪽보다 확장자 검증에 기대는 편을 택했다.

확장자와 MIME Type이 서로 어긋나면(예: `.pdf` + `image/png`) 거부한다.

PPT/PPTX는 MVP 범위에서 제외한다.

---

## 파일명 처리

사용자가 올린 파일명은 **표시용으로만** 쓴다. 저장 경로에는 절대 쓰지 않는다.

```
originalFileName  운영체제_1주차.pdf                              (DB, 화면 표시)
storedFileName    550e8400-e29b-41d4-a716-446655440000.pdf       (Storage 실제 이름)
```

경로가 섞여 들어오면 마지막 조각만 남긴다.

```
../../../etc/passwd.docx  →  passwd.docx
C:\Users\me\운영체제.pdf   →  운영체제.pdf
```

제어문자는 제거하고, 255자를 넘으면 잘라낸다.
점만 남는 이름(`.`, `..`)은 파일명으로 보지 않고 거부한다.

---

## 저장 구조

```
{storage-root}/
└── sessions/
    └── {sessionId}/            내부 UUID. 8자리 세션 코드가 아니다
        └── documents/
            ├── 550e8400-....pdf
            └── a6fa6d6e-....docx
```

경로에 세션 코드를 쓰지 않는 이유는, 코드가 곧 접근 키라서 파일 경로에 남기고 싶지 않기 때문이다.

DB에는 파일 본문을 넣지 않는다. 위치(`storagePath`)만 저장한다.

---

## 상태 값

```
UPLOADED      업로드 완료 (3단계에서 만들어지는 유일한 상태)
PARSING       텍스트 추출 중        (4단계)
PARSED        텍스트 추출 완료      (4단계)
PARSE_FAILED  텍스트 추출 실패      (4단계)
```

```
UPLOADED → PARSING → PARSED
                   ↘ PARSE_FAILED
```
