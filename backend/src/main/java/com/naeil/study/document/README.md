# document

강의자료(업로드된 파일) 도메인.

```
document
├── controller/   업로드 / 목록 / 삭제 API
├── service/      유스케이스 + Storage 보상 처리
├── repository/   Spring Data JPA
├── entity/       Document, DocumentFileType, DocumentStatus, DocumentPolicy
├── dto/          응답 record
├── exception/    도메인 예외
└── validation/   업로드 요청 검증
```

## 이번 단계 범위

파일을 **저장만** 한다. PDF/DOCX/TXT 내부 텍스트는 읽지 않는다.
텍스트 추출은 4단계에서 구현한다.

## 핵심 개념

### 파일명이 둘이다

```
originalFileName  사용자가 올린 이름. 화면 표시에만 쓴다
storedFileName    Storage 안의 실제 이름(UUID). 충돌과 경로 조작을 막는다
```

`../../../etc/passwd.pdf` 를 올려도 표시용 이름은 `passwd.pdf` 가 되고,
실제 저장 이름은 UUID다.

### 한 요청은 전체 성공하거나 전체 실패한다

```
전체 검증 → Storage 저장 → DB 저장(flush)
                   ↑              │ 실패
                   └── 보상 삭제 ─┘
```

파일 시스템은 DB 트랜잭션과 함께 롤백되지 않는다. 그래서 직접 보상 삭제를 한다.

### 다른 세션의 문서에 접근할 수 없다

조회 조건에 항상 세션 ID를 함께 건다.

```java
findByIdAndStudySessionId(documentId, sessionId)
```

문서 ID만으로 조회한 뒤 소유자를 비교하는 방식은 쓰지 않는다.
없는 문서와 남의 문서는 똑같이 404로 응답한다.

## 구현된 API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/sessions/{sessionCode}/documents` | 업로드 (multipart) |
| GET | `/api/sessions/{sessionCode}/documents` | 목록 조회 |
| DELETE | `/api/sessions/{sessionCode}/documents/{documentId}` | 삭제 |

명세: `docs/api/document-api.md` (저장소 루트 기준)

## 아직 없는 것

```
텍스트 추출 (PDF/DOCX/TXT)   STEP 4-2
extractedText 필드            STEP 4-2
PARSING / PARSED 상태 전이    STEP 4-2
파일 다운로드 API             계획 없음
PPT/PPTX, OCR                 MVP 제외
```
