# 문서 파싱 API 명세

Storage에 저장된 강의자료에서 텍스트를 추출해 DB에 저장한다.

이 단계의 책임은 **파일 → 평문 텍스트**까지다. 요약, 중요도 판단, Topic 분류는 하지 않는다.

- Base URL: `/api/sessions/{sessionCode}/documents`
- 인증: 없음. 8자리 세션 코드가 접근 키다.

## 목차

- [POST .../{documentId}/parse — 개별 파싱](#post-documentidparse--개별-파싱)
- [POST .../parse — 전체 파싱](#post-parse--전체-파싱)
- [상태 전이](#상태-전이)
- [형식별 처리 방식](#형식별-처리-방식)
- [텍스트 정규화 정책](#텍스트-정규화-정책)
- [파싱 성공 / 실패 조건](#파싱-성공--실패-조건)

---

## POST .../{documentId}/parse — 개별 파싱

```http
POST /api/sessions/7K2M9QXF/documents/550e8400-e29b-41d4-a716-446655440000/parse
```

### 응답 — 200 OK

```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "originalFileName": "운영체제_1주차.pdf",
  "status": "PARSED",
  "characterCount": 24831,
  "parsedAt": "2026-08-27T17:20:00"
}
```

추출한 텍스트 전체는 응답에 담지 않는다. 문서 하나가 수만 자에 이를 수 있다.
텍스트가 필요한 곳은 AI 분석 서비스이고, 그쪽은 API가 아니라 리포지터리로 읽는다.

### 상태별 동작

| 현재 상태 | 동작 |
| --- | --- |
| `UPLOADED` | 파싱한다 |
| `PARSE_FAILED` | 다시 시도한다 |
| `PARSED` | **파일을 다시 읽지 않고** 기존 결과를 그대로 돌려준다 (멱등) |
| `PARSING` | 409 `DOCUMENT_ALREADY_PARSING` |

### 부수 효과

```
lastAccessedAt = now
expiresAt      = now + 30일
```

세션 상태는 바꾸지 않는다. `UPLOADING`을 그대로 유지한다.
`ANALYZING`은 AI 분석이 실제로 시작될 때 전환한다.

### 오류

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_SESSION_CODE` | 세션 코드 형식 오류 |
| 400 | `INVALID_REQUEST` | `documentId`가 UUID 형식이 아님 |
| 404 | `SESSION_NOT_FOUND` | 세션 없음 |
| 404 | `DOCUMENT_NOT_FOUND` | 해당 세션에 그 문서가 없음 (다른 세션 소유 포함) |
| 409 | `DOCUMENT_ALREADY_PARSING` | 이미 파싱 중 |
| 422 | `DOCUMENT_PARSE_FAILED` | 파일 손상, 암호 걸린 PDF, 디코딩 실패 |
| 422 | `NO_EXTRACTABLE_TEXT` | 텍스트 레이어가 없는 PDF 등 |
| 422 | `STORED_FILE_NOT_FOUND` | 메타데이터는 있는데 실제 파일이 없음 |

실패하면 문서 상태가 `PARSE_FAILED`로 기록되고, 다시 요청해 재시도할 수 있다.

---

## POST .../parse — 전체 파싱

```http
POST /api/sessions/7K2M9QXF/documents/parse
```

### 대상

아직 파싱하지 않은(`UPLOADED`) 문서만 처리한다.

```
Document 1  UPLOADED  → 파싱
Document 2  PARSED    → 건너뜀
Document 3  UPLOADED  → 파싱
```

### 응답 — 200 OK

세션의 **전체 문서**를 담는다. 건너뛴 문서와 실패한 문서까지 함께 보여줘야
화면에서 지금 상태를 그대로 그릴 수 있다.

```json
{
  "documents": [
    {
      "documentId": "...",
      "originalFileName": "운영체제_1주차.pdf",
      "status": "PARSED",
      "characterCount": 24831,
      "parsedAt": "2026-08-27T17:20:00"
    },
    {
      "documentId": "...",
      "originalFileName": "스캔본.pdf",
      "status": "PARSE_FAILED",
      "characterCount": null,
      "parsedAt": null
    }
  ]
}
```

한 문서가 실패해도 나머지는 계속 처리한다. 따라서 응답은 항상 200이고,
성공 여부는 문서별 `status`로 확인한다.

---

## 상태 전이

```
UPLOADED ─┬─→ PARSING ─┬─→ PARSED
          │            └─→ PARSE_FAILED ─┐
          │                              │
          └──────────── 재시도 ←─────────┘
```

| 상태 | `extractedText` | `characterCount` | `parsedAt` | `parseErrorMessage` |
| --- | --- | --- | --- | --- |
| `UPLOADED` | null | null | null | null |
| `PARSING` | null | null | null | null |
| `PARSED` | 값 있음 | 텍스트 길이 | 완료 시각 | null |
| `PARSE_FAILED` | null | null | null | 내부 요약 |

`parseErrorMessage`는 진단용 내부 값이며 API 응답에 포함하지 않는다.
라이브러리 원문 메시지나 스택트레이스를 저장하지 않는다.

---

## 형식별 처리 방식

| 형식 | 라이브러리 | 처리 |
| --- | --- | --- |
| PDF | Apache PDFBox 3.0.8 | `PDFTextStripper` 로 텍스트 레이어 추출. 위치 기준 정렬 |
| DOCX | Apache POI 5.5.1 | `XWPFDocument` 본문 문단 + **표 안의 텍스트** |
| TXT | JDK | UTF-8 엄격 디코딩, 실패 시 MS949 재시도. BOM 제거 |

### PDF

**텍스트 레이어가 있는 PDF만 지원한다.** 스캔본처럼 이미지만 있는 PDF는
추출 결과가 비어 `NO_EXTRACTABLE_TEXT`로 실패한다. OCR은 MVP 범위가 아니다.

암호가 걸린 PDF는 `DOCUMENT_PARSE_FAILED`로 처리한다.

### DOCX

**표 안의 텍스트를 반드시 포함한다.** 강의자료는 표에 핵심 정리가 들어 있는 경우가 많아,
표를 빠뜨리면 학습에 필요한 내용이 통째로 사라진다.

문서 본문 요소를 순서대로 훑기 때문에 문단과 표의 등장 순서가 유지된다.
표는 셀을 탭으로, 행을 줄바꿈으로 구분한다.

### TXT

공식 지원 인코딩은 UTF-8이다. 다만 한국어 TXT는 메모장에서 CP949로 저장되는 경우가 흔해
UTF-8 엄격 디코딩에 실패하면 MS949로 한 번 더 시도한다. 둘 다 실패하면 파싱 실패다.

---

## 텍스트 정규화 정책

추출한 텍스트에 최소한의 정리만 한다.

### 하는 것

| 처리 | 이유 |
| --- | --- |
| CRLF, CR → LF | 줄바꿈 표기를 하나로 |
| 줄 끝 공백 제거 | 의미 없는 잡음 |
| 빈 줄 3개 이상 → 1개 | 문단 구분은 남기고 과한 여백만 정리 |
| 줄바꿈 없는 공백(U+00A0) → 일반 공백 | PDF에서 흔히 섞여 들어옴 |
| 폭 없는 문자(BOM, ZWSP) 제거 | 화면에 보이지 않는데 문자열 비교를 어긋나게 함 |
| 앞뒤 공백/빈 줄 제거 | |

### 하지 않는 것

| 처리 | 이유 |
| --- | --- |
| 줄바꿈 전부 제거 | 제목·소제목·목록 구조가 사라진다 |
| 줄 안의 연속 공백 축약 | 코드·수식·표 정렬이 무너진다 |
| 줄 앞 들여쓰기 제거 | 들여쓰기는 계층 구조 정보다 |

구조를 남기는 이유는 이후 AI가 제목, 소제목, 목록, 문단, 표를 구분해야 하기 때문이다.

```
나쁨:  1장 프로세스 1.1 프로세스란 ... 1.2 스레드란 ...

좋음:  1장 프로세스

       1.1 프로세스란
       ...
```

---

## 파싱 성공 / 실패 조건

### 성공

```
Storage 파일 로드 성공
Parser 정상 실행
정규화 후 텍스트 길이 >= 20자
```

최소 길이 기준(`DocumentPolicy.MIN_EXTRACTED_TEXT_LENGTH = 20`)을 두는 이유는,
스캔본 PDF에서 의미 없는 특수문자 몇 개만 추출되는 경우가 있기 때문이다.
너무 높게 잡으면 짧은 정상 문서가 거부되므로 낮게 둔다.

### 실패

```
Storage 파일 없음        → STORED_FILE_NOT_FOUND
파일 손상 / 암호 PDF      → DOCUMENT_PARSE_FAILED
디코딩 실패              → DOCUMENT_PARSE_FAILED
추출 텍스트 20자 미만     → NO_EXTRACTABLE_TEXT
```

---

## 트랜잭션 경계

파일 읽기와 텍스트 추출은 문서 크기에 따라 오래 걸린다.
그 시간 동안 DB 커넥션을 잡고 있지 않도록 상태 변경을 짧은 트랜잭션으로 나눴다.

```
tx1  상태를 PARSING 으로 변경하고 커밋
     ↓
     (트랜잭션 밖) Storage 읽기 + 텍스트 추출 + 정규화
     ↓
tx2  결과 저장 (PARSED) 또는 실패 기록 (PARSE_FAILED)
```

동시 요청은 `PARSING` 상태 검사로 막는다. 분산 락은 MVP 범위가 아니다.
