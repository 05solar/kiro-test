# document/controller

| 클래스 | 역할 |
| --- | --- |
| `DocumentController` | `/api/sessions/{sessionCode}/documents` 매핑 |

## 엔드포인트

| 메서드 | 경로 | 상태 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/sessions/{sessionCode}/documents` | 201 | 업로드 (multipart/form-data, 파트 이름 files) |
| GET | `/api/sessions/{sessionCode}/documents` | 200 | 목록 조회 |
| DELETE | `/api/sessions/{sessionCode}/documents/{documentId}` | 204 | 삭제 |

명세: `docs/api/document-api.md` (저장소 루트 기준)

## 모든 경로가 세션 아래에 있다

```
/api/sessions/{sessionCode}/documents/{documentId}
```

문서 ID만으로 접근하는 경로(/api/documents/{id})는 만들지 않는다.
세션 코드가 곧 접근 권한이므로, 경로에 세션이 없으면 소유 확인을 할 근거가 사라진다.

## 이 클래스가 하는 일

```
HTTP 요청 → 서비스 호출 → DTO 변환 → ResponseEntity
```

파일 검증, 저장, 보상 처리는 모두 서비스 아래에 있다.
