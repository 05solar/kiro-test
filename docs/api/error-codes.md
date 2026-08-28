# 공통 에러 코드

모든 에러 응답은 형식이 같다. 클라이언트는 `code`로 분기하고, `message`는 사용자에게 그대로 보여줄 수 있다.

```json
{
  "code": "SESSION_NOT_FOUND",
  "message": "유효한 학습 세션을 찾을 수 없습니다."
}
```

## 코드 목록

| code | HTTP | 메시지 | 발생 조건 |
| --- | --- | --- | --- |
| `INVALID_SESSION_CODE` | 400 | 올바르지 않은 세션 코드입니다. | 세션 코드가 8자리가 아니거나 허용하지 않는 문자를 포함 |
| `INVALID_EXAM_TIME` | 400 | 시험 시간은 현재 시간보다 이후여야 합니다. | 시험 일시가 현재 시각과 같거나 과거 |
| `EMPTY_FILE` | 400 | 빈 파일은 업로드할 수 없습니다. | 내용이 없는 파일을 업로드 |
| `UNSUPPORTED_FILE_TYPE` | 400 | PDF, DOCX, TXT 파일만 업로드할 수 있습니다. | 허용하지 않는 형식, 또는 확장자와 MIME Type 불일치 |
| `FILE_SIZE_EXCEEDED` | 400 | 파일 하나당 최대 20MB까지 업로드할 수 있습니다. | 개별 파일 크기 초과 |
| `FILE_COUNT_EXCEEDED` | 400 | 강의자료는 최대 10개까지 업로드할 수 있습니다. | 세션 파일 개수 초과 |
| `SESSION_STORAGE_EXCEEDED` | 400 | 강의자료 전체 용량은 100MB를 넘을 수 없습니다. | 세션 전체 용량 초과 |
| `EXAM_INFO_REQUIRED` | 400 | 시험 정보를 먼저 입력해 주세요. | 시험 정보 없이 AI 분석 요청 |
| `TOPICS_REQUIRED` | 400 | 강의자료 분석을 먼저 완료해 주세요. | 분석된 Topic 없이 학습 계획 요청 |
| `SESSION_NOT_READY` | 400 | 강의자료 분석이 끝난 뒤에 학습 계획을 만들 수 있습니다. | READY 가 아닌 세션에서 학습 계획 요청 |
| `NO_STUDY_TIME_AVAILABLE` | 400 | 남은 학습 시간이 없습니다. 시험 정보를 다시 확인해 주세요. | 남은 학습 시간이 0 이하 |
| `NO_PARSED_DOCUMENT` | 400 | 분석할 수 있는 강의자료가 없습니다. 자료를 올리고 내용을 먼저 읽어 주세요. | PARSED 자료 없이 AI 분석 요청 |
| `INVALID_QUIZ_OPTION` | 400 | 보기 번호가 올바르지 않습니다. | 답안의 보기 번호가 0~3 을 벗어남 |
| `NO_QUIZ_SOURCE_CONTEXT` | 400 | 퀴즈를 만들 강의자료 내용을 찾을 수 없습니다. | 출처 문서가 없거나 추출된 텍스트가 없음 |
| `INVALID_REQUEST` | 400 | 요청 값이 올바르지 않습니다. (필드 검증 실패 시 해당 항목의 안내 문구로 대체) | Bean Validation 실패, 본문 형식 오류 |
| `SESSION_NOT_FOUND` | 404 | 유효한 학습 세션을 찾을 수 없습니다. | 해당 코드의 세션이 없음 |
| `CURRICULUM_NOT_FOUND` | 404 | 학습 계획을 찾을 수 없습니다. | 아직 학습 계획을 만들지 않음 |
| `STUDY_STEP_NOT_FOUND` | 404 | 해당 학습 단계를 찾을 수 없습니다. | 없거나 다른 세션의 학습 단계 |
| `TOPIC_NOT_FOUND` | 404 | 해당 학습 주제를 찾을 수 없습니다. | 없거나 다른 세션의 Topic |
| `QUIZ_NOT_FOUND` | 404 | 해당 퀴즈를 찾을 수 없습니다. | 없거나 다른 세션의 퀴즈, 또는 아직 생성 전 |
| `WRONG_ANSWER_SUMMARY_NOT_FOUND` | 404 | 오답 복습 요약을 찾을 수 없습니다. | 아직 요약을 생성하지 않음 |
| `DOCUMENT_NOT_FOUND` | 404 | 해당 강의자료를 찾을 수 없습니다. | 세션에 그 문서가 없음 (다른 세션 소유 포함) |
| `DOCUMENT_ALREADY_PARSING` | 409 | 이미 문서를 읽는 중입니다. 잠시 후 다시 확인해 주세요. | 파싱 중인 문서에 다시 파싱 요청 |
| `ANALYSIS_ALREADY_RUNNING` | 409 | 이미 강의자료를 분석하고 있습니다. 잠시 후 다시 확인해 주세요. | 분석 중인 세션에 다시 분석 요청 |
| `STUDY_STEP_ALREADY_COMPLETED` | 409 | 이미 완료한 학습 단계입니다. | 완료한 단계를 다시 시작하려 함 |
| `STUDY_STEP_NOT_STARTED` | 409 | 아직 시작하지 않은 학습 단계입니다. | 시작하지 않은 단계를 완료하려 함 |
| `INVALID_STUDY_STEP_ORDER` | 409 | 앞선 학습 단계를 먼저 진행해 주세요. | 앞선 단계를 건너뛰고 시작하려 함 |
| `ANOTHER_STEP_IN_PROGRESS` | 409 | 진행 중인 학습 단계가 있습니다. 먼저 완료해 주세요. | 이미 다른 단계가 진행 중 |
| `EXAM_ALREADY_STARTED` | 409 | 시험 시간이 지나 새로운 학습을 시작할 수 없습니다. | 시험 시각이 지난 뒤 새 단계 시작 |
| `TOPIC_STUDY_NOT_COMPLETED` | 409 | 해당 주제의 학습을 먼저 완료해 주세요. | 학습 단계 완료 전(계획 미포함·SKIPPED 포함) 퀴즈 생성 요청 |
| `QUIZ_NOT_COMPLETED` | 409 | 아직 풀지 않은 퀴즈가 있습니다. 모든 문제를 푼 뒤 다시 시도해 주세요. | 퀴즈를 다 풀기 전에 오답 요약 요청 |
| `QUIZ_GENERATION_IN_PROGRESS` | 409 | 새로운 퀴즈를 만들고 있습니다. 잠시만 기다려 주세요. | 같은 Topic 의 새 회차 생성이 진행 중 |
| `DOCUMENT_PARSE_FAILED` | 422 | 문서 내용을 읽는 중 오류가 발생했습니다. | 파일 손상, 암호 걸린 PDF, 디코딩 실패 |
| `CURRICULUM_GENERATION_FAILED` | 422 | 현재 남은 시간으로 학습 계획을 생성할 수 없습니다. | 남은 시간이 최소 학습시간 미만이거나 제약을 만족하는 계획이 없음 |
| `NO_EXTRACTABLE_TEXT` | 422 | 문서에서 학습에 사용할 텍스트를 추출할 수 없습니다. | 텍스트 레이어가 없는 PDF 등 |
| `STORED_FILE_NOT_FOUND` | 422 | 저장된 파일을 찾을 수 없습니다. 파일을 다시 업로드해 주세요. | 메타데이터는 있는데 실제 파일이 없음 |
| `ANALYSIS_FAILED` | 502 | 자료 분석에 실패했습니다. 다시 시도해 주세요. | AI 호출 실패, 응답 검증 실패, 결과 없음 |
| `QUIZ_GENERATION_FAILED` | 502 | 퀴즈 생성에 실패했습니다. 다시 시도해 주세요. | AI 호출 실패 또는 퀴즈 응답 검증 실패 |
| `WRONG_ANSWER_SUMMARY_GENERATION_FAILED` | 502 | 복습 요약 생성에 실패했습니다. 다시 시도해 주세요. | AI 호출 실패 또는 요약 응답 검증 실패 (기존 요약은 보존) |
| `FILE_STORAGE_FAILED` | 500 | 파일 저장 중 오류가 발생했습니다. | 파일 저장소 읽기/쓰기/삭제 실패 |
| `SESSION_CODE_GENERATION_FAILED` | 500 | 세션 코드를 생성하지 못했습니다. 잠시 후 다시 시도해 주세요. | 최대 재시도 횟수 안에 고유 코드 생성 실패 |
| `INTERNAL_ERROR` | 500 | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. | 처리하지 못한 예외 |

## 설계 원칙

### 1. 세션 존재 여부를 노출하지 않는다

존재하지 않는 세션과 만료되어 삭제된 세션을 구분해서 알려주지 않는다.
둘 다 `SESSION_NOT_FOUND`로 같은 메시지를 반환한다.
8자리 코드가 곧 접근 키이므로, 응답 차이는 코드 추측(brute force)에 힌트가 된다.

> 만료 안내가 필요한 화면(`해당 학습 세션은 보관 기간이 지나 삭제되었습니다.`)은
> 클라이언트가 LocalStorage에 남은 최근 코드로 조회했을 때만 문구를 바꿔 보여준다.
> 서버 응답은 동일하게 유지한다.

### 2. 형식 오류는 DB에 닿기 전에 끊는다

세션 코드 형식 검증(`SessionCodePolicy.isValid`)에 실패하면 조회 없이 400을 반환한다.
무작위 문자열로 DB를 두드리는 요청을 걸러낸다.

### 3. 내부 정보를 응답에 넣지 않는다

내부 UUID, 생성 시각, 스택트레이스, 예외 메시지는 응답에 포함하지 않는다.
처리하지 못한 예외는 `INTERNAL_ERROR`로 바꾸고 서버 로그에만 원본을 남긴다.

## 구현 위치

```
com.naeil.study.common.exception.ErrorCode              코드 - HTTP 상태 - 메시지 매핑
com.naeil.study.common.exception.BusinessException      모든 도메인 예외의 상위 타입
com.naeil.study.common.exception.GlobalExceptionHandler @RestControllerAdvice
com.naeil.study.common.dto.ErrorResponse                응답 형식
```

새 에러를 추가할 때:

1. `ErrorCode`에 항목을 추가한다 (HTTP 상태와 사용자 메시지 포함)
2. `BusinessException`을 상속한 예외 클래스를 도메인 패키지의 `exception`에 만든다
3. `GlobalExceptionHandler`는 고치지 않는다 — `BusinessException` 하나로 처리된다
4. 이 문서의 표에 한 줄 추가한다
