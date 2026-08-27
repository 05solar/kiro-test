# API 문서

이 폴더는 백엔드 REST API의 명세를 담는다. 구현과 문서가 어긋나면 문서가 틀린 것으로 본다.
API를 바꾸면 같은 커밋에서 이 폴더의 문서도 고친다.

## 문서 목록

| 문서 | 내용 |
| --- | --- |
| [session-api.md](session-api.md) | 세션 생성 / 조회 / 시험 정보 API |
| [document-api.md](document-api.md) | 강의자료 업로드 / 목록 / 삭제 API |
| [document-parsing-api.md](document-parsing-api.md) | 문서 텍스트 추출 API |
| [study-context-api.md](study-context-api.md) | 학습 맥락 저장 / 조회 API |
| [analysis-api.md](analysis-api.md) | AI 분석 실행 / Topic 조회 API |
| [curriculum-api.md](curriculum-api.md) | 학습 계획 생성 / 조회 API |
| [study-step-api.md](study-step-api.md) | 학습 단계 시작 / 완료 API |
| [error-codes.md](error-codes.md) | 공통 에러 응답 형식과 코드 목록 |

## 공통 규약

- Base URL: `/api`
- 요청/응답 형식: `application/json` (파일 업로드만 `multipart/form-data`)
- 날짜/시각: ISO-8601 문자열 (`2026-08-27T15:30:00`)
- 인증: 없음. 8자리 세션 코드가 접근 키다.
- 에러 응답 형식: `{ "code": "...", "message": "..." }`

## 구현 현황

| 메서드 | 경로 | 상태 |
| --- | --- | --- |
| POST | `/api/sessions` | 구현 완료 |
| GET | `/api/sessions/{sessionCode}` | 구현 완료 |
| PUT | `/api/sessions/{sessionCode}/exam` | 구현 완료 |
| POST | `/api/sessions/{sessionCode}/documents` | 구현 완료 |
| GET | `/api/sessions/{sessionCode}/documents` | 구현 완료 |
| DELETE | `/api/sessions/{sessionCode}/documents/{documentId}` | 구현 완료 |
| POST | `/api/sessions/{sessionCode}/documents/parse` | 구현 완료 |
| POST | `/api/sessions/{sessionCode}/documents/{documentId}/parse` | 구현 완료 |
| PUT | `/api/sessions/{sessionCode}/study-context` | 구현 완료 |
| GET | `/api/sessions/{sessionCode}/study-context` | 구현 완료 |
| POST | `/api/sessions/{sessionCode}/analysis` | 구현 완료 |
| GET | `/api/sessions/{sessionCode}/topics` | 구현 완료 |
| POST | `/api/sessions/{sessionCode}/curriculum` | 구현 완료 |
| GET | `/api/sessions/{sessionCode}/curriculum` | 구현 완료 |
| POST | `/api/sessions/{sessionCode}/steps/{stepId}/start` | 구현 완료 |
| POST | `/api/sessions/{sessionCode}/steps/{stepId}/complete` | 구현 완료 |
| GET | `/api/sessions/{sessionCode}/steps/{stepId}` | 만들지 않음 — 계획 조회로 충분 |
| GET | `/api/sessions/{sessionCode}/topics/{topicId}/quizzes` | 예정 (STEP 9) |
| POST | `/api/sessions/{sessionCode}/quizzes/{quizId}/answer` | 예정 (STEP 9) |
| GET | `/api/sessions/{sessionCode}/progress` | 만들지 않음 — 계획 조회에 포함 |

예정 API의 최종 형태는 구현 시점에 확정한다. 위 표는 기획 문서 기준의 계획이다.
