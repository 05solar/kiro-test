# 개발 진행 절차

이 문서는 "무엇을 어떤 순서로, 무엇을 확인하고 다음으로 넘어가는가"를 기록한다.
각 단계는 **검증을 통과해야만** 다음 단계로 넘어간다. 검증에 실패하면 다음 명령을 실행하지 않고
직전 단계를 다시 검토한다.

## 단계별 진행 원칙

```
명령 실행
   ↓
검증 (테스트 / 빌드 / 실제 호출)
   ↓
통과? ── 아니오 ──> 직전 단계 재검토
   │
  예
   ↓
다음 단계
```

## STEP 1~2 — 프로젝트 기본 구조 + 세션 생성/복구 (완료)

### 수행한 작업

1. Gradle + Spring Boot 3.5.16 + Java 21 프로젝트 구성
2. 기능별 패키지 구조 확정 (`session`, `common`)
3. `StudySession` 엔티티 및 `SessionStatus` Enum 구현
4. `SessionCodeGenerator` (SecureRandom 기반 8자리 코드) 구현
5. `StudySessionRepository` 구현
6. `SessionService` (생성 / 조회 + 접근시각 갱신) 구현
7. DTO 분리 (`CreateSessionResponse`, `SessionResponse`, `ErrorResponse`)
8. `SessionController` REST API 구현
9. 세션 코드 형식 검증 (`SessionCodePolicy`)
10. 예외 처리 (`GlobalExceptionHandler` + `BusinessException` 계층)
11. 테스트 작성 (단위 / 슬라이스 / 통합)
12. 전체 테스트 및 빌드

### 검증 기록

| # | 검증 항목 | 방법 | 결과 |
| --- | --- | --- | --- |
| 1 | 빌드 툴체인 동작 | `./gradlew dependencies` | 통과 |
| 2 | 메인 소스 컴파일 | `./gradlew compileJava` | 통과 |
| 3 | 전체 테스트 | `./gradlew test` | 통과 (144 tests, 0 failures) |
| 4 | 구조 수정 반영 후 전체 빌드 | `./gradlew clean build` | 통과 |
| 5 | 실제 PostgreSQL 연동 | Docker + 실제 HTTP 호출 | [backend/PROCESS.md](backend/PROCESS.md) 참고 |

### 검증 실패와 조치 기록

실패한 검증은 원인을 찾아 조치한 뒤 재검증했다. 같은 문제를 다시 겪지 않도록 남긴다.

| 실패 | 원인 | 조치 |
| --- | --- | --- |
| 모든 테스트가 `ClassNotFoundException` | Gradle이 클래스패스를 `@argfile`로 넘기는데, Gradle은 UTF-8로 쓰고 `java.exe`는 네이티브 인코딩(MS949)으로 읽어 한글 경로가 깨짐 | `gradle.properties`에 `org.gradle.jvmargs=-Dfile.encoding=MS949` 지정 |
| 세션 코드 문자 집합 테스트 2건 실패 | 명세의 허용 문자열에는 `L`이 포함되는데 제외 목록에는 `L`이 있어 모순 | 명시된 문자열(32자)을 정본으로 채택. [AGENT.md](AGENT.md)의 "명세 모순" 항목 참고 |
| `./gradlew clean` 실패 | 이전 명령이 `build/` 하위에 작업 디렉터리를 잡고 있었음 | 상위 디렉터리에서 재실행 |

## STEP 3 — 시험 정보 입력 (완료)

### 수행한 작업

1. `UpdateExamRequest` / `ExamResponse` DTO 추가 (Bean Validation)
2. `StudySession.updateExamInfo(...)` 도메인 메서드 추가
3. `SessionService.updateExamInfo(...)` + `calculateEffectiveStudyMinutes(...)` 구현
4. `PUT /api/sessions/{sessionCode}/exam` 구현
5. `INVALID_EXAM_TIME` 에러 코드와 `InvalidExamTimeException` 추가
6. `GlobalExceptionHandler`에 필드 오류 메시지 전달, 본문 파싱 오류(400) 처리 추가
7. 테스트 29건 추가

### 핵심 결정

시험까지 남은 실제 시간보다 큰 학습시간을 입력할 수 있으므로 서버가 다시 계산한다.

```
availableStudyMinutes = 사용자 입력 원본값 (진행 중 불변)
remainingStudyMinutes = min(입력값, 시험까지 남은 분)
```

시간 계산은 `ChronoUnit.MINUTES` 기준이며 초 단위는 버린다.
`@Future` 대신 서비스에서 `Clock`으로 검증한다. `@Future`는 시스템 시계를 보기 때문에
시간을 고정한 테스트와 어긋난다.

### 검증 기록

| # | 검증 항목 | 방법 | 결과 |
| --- | --- | --- | --- |
| 1 | 컴파일 | `./gradlew compileJava` | 통과 |
| 2 | 전체 테스트 | `./gradlew test` | 통과 (173 tests, 0 failures) |
| 3 | 실제 PostgreSQL E2E | Docker + curl | 통과 |
| 4 | 전체 빌드 | `./gradlew clean build` | 통과 |

E2E에서 확인한 내용:

```
시험까지 240분 / 입력 360분  → available=360, remaining=239 (초 단위 버림)
3일 뒤 시험 / 입력 420분     → available=420, remaining=420
과거 시험시간               → 400 INVALID_EXAM_TIME
학습시간 0 / 999999          → 400 INVALID_REQUEST (항목별 안내 메시지)
없는 세션 / 잘못된 코드 형식  → 404 / 400
GET 응답에 시험 정보 포함     → 확인
```

### 검증 실패와 조치 기록

| 실패 | 원인 | 조치 |
| --- | --- | --- |
| E2E에서 한글 과목명 요청이 전부 400 | Git Bash가 `curl -d`의 한글 인자를 MS949로 변환해 전송 (앱 로그: `Invalid UTF-8 start byte 0xbf`) | 요청 본문을 UTF-8 파일로 저장 후 `--data-binary @file` 로 전송. 앱 문제가 아님 |
| 문서 표에 중복 줄 발생 | 앞선 sed 체인이 오류 전까지 이미 적용된 상태에서 재실행 | 인접 중복 줄 제거 후 재확인 |

## STEP 4 — 강의자료 업로드 및 파일 저장 (완료)

### 수행한 작업

1. `storage` 패키지: `StorageService` 추상화 + `LocalStorageService` 구현
2. `document` 패키지: `Document` 엔티티, `DocumentFileType`, `DocumentStatus`, `DocumentPolicy`
3. `DocumentFileValidator` — 저장 전 전체 검증
4. `DocumentService` — 업로드 / 목록 / 삭제 + Storage 보상 처리
5. `DocumentController` — POST / GET / DELETE
6. 에러 코드 7종 추가, multipart 예외 처리 추가
7. 테스트 99건 추가

**텍스트 추출은 하지 않았다.** 파일은 원본 그대로 저장만 한다.

### 핵심 결정

**파일명을 둘로 나눴다.**

```
originalFileName  사용자가 올린 이름. 표시용
storedFileName    Storage 실제 이름(UUID). 경로 조작과 충돌을 함께 막는다
```

**한 요청은 전체 성공하거나 전체 실패한다.** 파일 시스템은 DB 트랜잭션과 함께 롤백되지
않으므로, 전체 검증 후 저장하고 실패 시 보상 삭제한다. DB 저장에 `saveAllAndFlush`를
쓰는 이유는 커밋 시점 실패를 서비스 메서드 안에서 잡기 위해서다.

**삭제는 DB 먼저, Storage 나중이다.** 반대 순서라면 DB 삭제 실패 시 파일 없는 메타데이터가
남는다. 지금 순서에서는 최악의 경우 참조되지 않는 파일만 남고 세션 만료 시 정리된다.

**다른 세션의 문서는 조회 자체가 안 된다.** `findByIdAndStudySessionId`로 세션 조건을
쿼리에 넣었다. 응답은 403이 아니라 404다.

### 검증 기록

| # | 검증 항목 | 방법 | 결과 |
| --- | --- | --- | --- |
| 1 | 컴파일 | `./gradlew compileJava` | 통과 |
| 2 | 전체 테스트 | `./gradlew test` | 통과 (272 tests, 0 failures) |
| 3 | 실제 PostgreSQL E2E | Docker + curl | 통과 |
| 4 | 전체 빌드 | `./gradlew clean build` | 통과 |

E2E에서 확인한 내용:

```
파일 3개 업로드                → 201, 세션 상태 CREATED → UPLOADING
목록 조회                      → 200, 업로드 순서대로 3건
Storage 경로                   sessions/{sessionId}/documents/{uuid}.{ext}
한글 파일명                    운영체제_1주차.pdf 정상 저장
경로 조작 파일명                ../../../etc/passwd.docx → passwd.docx
다른 세션이 남의 문서 삭제 시도  → 404 DOCUMENT_NOT_FOUND
소유 세션 삭제                  → 204, DB와 파일 모두 제거
지원하지 않는 형식 섞기          → 400, 저장 파일 수 변화 없음
빈 파일                        → 400 EMPTY_FILE
documentId 형식 오류            → 400 INVALID_REQUEST
```

### 검증 실패와 조치 기록

| 실패 | 원인 | 조치 |
| --- | --- | --- |
| `MissingServletRequestPartException` 컴파일 오류 | 패키지가 `web.bind`가 아니라 `web.multipart.support` | import 수정 |
| E2E에서 한글 파일명이 깨져 저장됨 | Git Bash가 `curl -F`의 파일명 인자를 MS949로 변환 (2단계와 같은 문제) | multipart 본문을 UTF-8 파일로 직접 만들어 `--data-binary` 로 전송해 재검증 |

## STEP 4-2 — PDF/DOCX/TXT 텍스트 추출 (완료)

### 수행한 작업

1. 의존성 추가: Apache PDFBox 3.0.8, Apache POI 5.5.1
2. `Document` 에 파싱 필드 추가 (`extractedText`, `characterCount`, `parsedAt`, `parseErrorMessage`)
   와 상태 전이 메서드 (`startParsing` / `markParsed` / `markParseFailed`)
3. `document/parser` 패키지: `DocumentParser` 인터페이스 + PDF/DOCX/TXT 구현 + 팩터리
4. `TextNormalizer` — 구조를 남기는 최소 정규화
5. `DocumentParsingService` (오케스트레이션) + `DocumentParseStateWriter` (짧은 트랜잭션)
6. 개별 파싱 / 전체 파싱 API, 에러 코드 5종
7. 테스트 70건 추가

### 핵심 결정

**트랜잭션을 셋으로 나눴다.** 파일 읽기와 추출은 오래 걸릴 수 있어 그 시간 동안 DB 커넥션을
잡지 않는다. `PARSING` 으로 바꾸고 커밋 → 트랜잭션 밖에서 추출 → 결과 저장.
자기 호출은 프록시를 타지 않으므로 상태 변경만 별도 빈(`DocumentParseStateWriter`)으로 분리했다.

**정규화는 구조를 남긴다.** 줄바꿈, 들여쓰기, 표의 탭을 지우지 않는다.
이후 AI가 제목·소제목·목록·표를 구분해야 하기 때문이다.
줄 안의 연속 공백도 축약하지 않는다. 코드·수식·표 정렬이 무너진다.

**이미 파싱된 문서는 다시 읽지 않는다.** 같은 요청에 기존 결과를 그대로 돌려준다(멱등).
실패한 문서는 다시 요청하면 재시도한다.

### 검증 기록

| # | 검증 항목 | 방법 | 결과 |
| --- | --- | --- | --- |
| 1 | 컴파일 | `./gradlew compileJava` | 통과 |
| 2 | 전체 테스트 | `./gradlew test` | 통과 (342 tests, 0 failures) |
| 3 | 실제 PostgreSQL E2E | Docker + curl | 통과 (아래 실패 기록 참고) |
| 4 | 전체 빌드 | `./gradlew clean build` | 통과 |

### 검증 실패와 조치 기록

| 실패 | 원인 | 조치 |
| --- | --- | --- |
| PostgreSQL에서 `extracted_text` 에 본문 대신 `16411` 같은 숫자가 저장됨 | `@Lob String` 을 쓰면 PostgreSQL JDBC 드라이버가 large object로 저장하고 컬럼에는 OID만 남긴다. H2에서는 정상 동작해 342개 테스트가 모두 통과했다 | `@Lob` 제거, `columnDefinition = "TEXT"` 만 유지. 재검증 후 본문이 정상 저장됨 |
| 통합 테스트 2건이 `parsedAt` 문자열 비교에서 실패 | 응답 JSON(`.5028402`)과 DB 재조회 값(`.50284`)의 정밀도가 다르다. 로직 문제가 아니다 | 재파싱 여부는 DB 값끼리 비교하도록 단언 변경 |

이 첫 번째 실패는 **H2 테스트만으로는 잡을 수 없는 문제**였다.
단계마다 실제 PostgreSQL로 확인하는 절차가 없었다면 5단계 이후에야 드러났을 것이다.

## STEP 5 — 학습 맥락(StudyContext) (완료)

### 수행한 작업

1. `studycontext` 패키지: `StudyContext` 엔티티, `StudyContextPolicy`
2. `StudyContextRepository` (`findByStudySessionId`)
3. `UpdateStudyContextRequest` / `StudyContextResponse`
4. `StudyContextService` — Upsert + 조회
5. `PUT` / `GET /api/sessions/{sessionCode}/study-context`
6. 테스트 50건 추가

AI 호출은 하지 않는다. 가중치 계산도 하지 않는다. 저장과 조회만 한다.

### 핵심 결정

**세션당 하나로 제약을 걸었다.** `session_id` 에 UNIQUE. 둘 이상 생기면 어느 것이
최신인지 코드가 판단해야 하는데, 제약으로 막으면 그 판단 자체가 사라진다.

**미입력 조회는 404가 아니라 200 + 전부 null이다.** 학습 맥락은 선택 입력이라
"없음"이 정상 상태다. 404로 응답하면 프론트가 정상 상태를 예외로 다뤄야 한다.

**PUT은 전체 교체다.** 그래서 DELETE API를 만들지 않았다. 네 항목을 모두 비운 채
PUT하면 삭제와 같은 효과가 난다.

**공백만 있는 입력은 null로 저장한다.** 빈 문자열과 null이 섞이면 "입력하지 않음"을
판단하는 조건이 여기저기서 달라진다.

**새 에러 코드를 추가하지 않았다.** 필요한 상황이 기존 `SESSION_NOT_FOUND`,
`INVALID_SESSION_CODE`, `INVALID_REQUEST` 로 모두 표현된다.

### 검증 기록

| # | 검증 항목 | 방법 | 결과 |
| --- | --- | --- | --- |
| 1 | 컴파일 | `./gradlew compileJava` | 통과 |
| 2 | 전체 테스트 | `./gradlew test` | 통과 (392 tests, 0 failures) |
| 3 | 실제 PostgreSQL E2E | Docker + curl | 통과 |
| 4 | 전체 빌드 | `./gradlew clean build` | 통과 |

4단계에서 `@Lob` 문제를 겪었으므로 신규 TEXT 컬럼 4개를 실제 PostgreSQL로 확인했다.
`professor_emphasis`, `past_exam_info`, `weak_areas`, `must_study_areas` 모두
`text` 타입에 본문이 그대로 저장되고 OID가 아닌 것을 확인했다.

E2E에서 확인한 내용:

```
미입력 조회                  200 + 전부 null
저장 (공백 포함 입력)         200, 앞뒤 공백 제거 / 공백만 → null
조회                        저장한 값 그대로
수정 (전체 교체)             200, 보내지 않은 항목은 비워짐
2001자 / 2000자             400 / 200 (DB 저장 길이 2000 확인)
없는 세션 PUT / GET          404
코드 형식 오류                400
다른 세션 조회                전부 null (격리)
세션 상태                    CREATED 유지
study_contexts 행 개수        1 (Upsert가 행을 늘리지 않음)
```

이번 단계에서는 검증 실패 없이 통과했다.

## STEP 6 — AI 강의자료 분석 및 Topic 생성 (완료)

### 수행한 작업

1. 의존성 추가: Anthropic Java SDK (`com.anthropic:anthropic-java:2.34.0`)
2. `topic` 패키지: `Topic` 엔티티(jsonb 2개), `TopicImportance`, 리포지터리, 조회 API
3. `analysis` 패키지: `AiAnalysisClient` 추상화 + `ClaudeAnalysisClient` + 키 없을 때 대체 구현
4. `DocumentChunker` — 문단 경계 우선, 글자 수 기준
5. `AnalysisPrompts` — 시스템 지시문과 데이터 분리, 주입 대비, Grounding 규칙
6. `AiTopicResponseValidator` — 실패/보정 두 갈래 검증
7. `AnalysisService`(트랜잭션 없음) + `AnalysisStateWriter`(짧은 트랜잭션)
8. `POST /analysis`, `GET /topics`, 에러 코드 4종, `ANALYSIS_FAILED` 상태 추가
9. 테스트 83건 추가 (실제 AI API를 부르지 않는다)

### 핵심 결정

**공급자는 Claude(`claude-opus-5`)를 골랐다.** 명세는 공급자를 열어 두었고 프로젝트에
다른 LLM 코드가 없었다. 도메인은 `AiAnalysisClient` 인터페이스만 보므로 교체 비용은 낮다.

**분석을 두 단계로 나눴다.** 여러 문서를 한 번에 보내면 컨텍스트 한도를 넘고,
조각마다 최종 Topic을 만들면 같은 주제가 여러 개로 쪼개진다.
조각에서는 후보만 뽑고 합치는 판단은 전체를 보는 통합 단계에서 한다.

**남은 학습 시간을 이 단계에서 쓰지 않는다.** 시간이 부족하다고 Topic을 빼면
"무엇을 배울 수 있는가"와 "무엇을 배울 시간이 있는가"가 뒤엉킨다.
Topic 예상시간 합이 남은 시간을 넘어도 그대로 저장한다.

**숫자 가중치를 하드코딩하지 않았다.** `교수 강조 × 2.0` 같은 계수를 임의로 정하지 않고,
자료와 학습 맥락을 함께 놓고 LLM이 판단하게 했다.

**AI에게 내부 UUID를 주지 않는다.** `DOC_1` 참조값을 주고 서버가 되돌린다.
LLM이 그럴듯한 UUID를 지어내면 존재하지 않는 문서를 가리키게 된다.

**검증을 실패와 보정으로 나눴다.** 구조가 깨진 응답은 전체 실패, 값이 조금 벗어난 것은
보정 후 진행. 전체 실패는 분석 한 번의 비용과 대기 시간을 다시 쓰게 만든다.

### 검증 기록

| # | 검증 항목 | 방법 | 결과 |
| --- | --- | --- | --- |
| 1 | 컴파일 | `./gradlew compileJava` | 통과 |
| 2 | 전체 테스트 | `./gradlew test` | 통과 (475 tests, 0 failures) |
| 3 | 실제 PostgreSQL API E2E | Docker + curl | 통과 |
| 4 | 실제 PostgreSQL 통합 테스트 | 통합 테스트를 PostgreSQL로 재실행 | 통과 (14건) |
| 5 | 전체 빌드 | `./gradlew clean build` | 통과 |

4번은 4단계의 `@Lob` 사고 때문에 추가한 절차다. 신규 `jsonb` 컬럼 2개의 쓰기·읽기가
실제 PostgreSQL에서 동작하는지 H2가 아닌 곳에서 확인했다.

```bash
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:55432/naeil_study" \
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect \
./gradlew test --tests "*AnalysisApiIntegrationTest*"
```

E2E에서 확인한 내용:

```
시험 정보 없이 분석          400 EXAM_INFO_REQUIRED
PARSED 자료 없이 분석        400 NO_PARSED_DOCUMENT
분석 전 Topic 조회           200 + 빈 배열
AI 키 미설정 상태에서 분석    502 ANALYSIS_FAILED
세션 상태                   ANALYSIS_FAILED (자료와 학습 맥락은 그대로)
없는 세션                   404
topics 테이블               key_points / source_document_ids 가 jsonb
study_sessions CHECK 제약    ANALYSIS_FAILED 포함
```

### 검증 실패와 조치 기록

| 실패 | 원인 | 조치 |
| --- | --- | --- |
| 통합 테스트에서 이전 테스트의 AI 요청 기록이 섞임 | `FakeAiAnalysisClient` 가 스프링 싱글턴이라 기록이 누적됨 | `reset()` 을 추가하고 `@BeforeEach` 에서 호출 |
| 청크 경계 테스트 실패 | 경계 탐색 범위(목표 크기의 뒤 20%) 밖에 빈 줄을 배치한 테스트 데이터 문제 | 빈 줄이 탐색 범위 안에 오도록 데이터 수정 |
| `importance="very_high "` 가 통과 | 검증기가 의도적으로 공백·대소문자를 관대하게 처리 | 테스트의 잘못된 기대를 수정 |
| 재분석 테스트가 502 | 앞선 sed 편집이 테스트 본문의 복구 줄을 함께 삭제 | 삭제된 줄 복구 |

## STEP 7 — 남은 시간 기반 최초 학습 계획 (완료)

### 수행한 작업

1. `curriculum` 패키지: `Curriculum`, `StudyStep` 엔티티 + Enum 4종
2. `CurriculumPlanner` + `CurriculumPolicy` — 규칙 기반 시간 배분
3. `CurriculumService` — 검증, 시험 시간 재확인, 저장
4. `POST` / `GET /api/sessions/{code}/curriculum`
5. 에러 코드 5종
6. 테스트 128건 추가 (무작위 입력 60회 포함)

### 핵심 결정

**AI를 쓰지 않았다.** 중요도, 예상 시간, 학습 맥락 일치는 이미 분석 단계에서 AI가 정했다.
남은 일은 시간을 나누는 것이고 규칙이 더 낫다. LLM에게 "180분에 맞춰 나눠 줘"라고 맡기면
합이 200분이 되거나 반드시 넣어야 할 주제가 조용히 빠진다.

**숫자 점수를 쓰지 않았다.** `VERY_HIGH = 100, 교수 강조 = +30` 같은 계수는 근거 없이
정해지고 아무도 왜 그 값인지 설명하지 못한다. 무엇이 무엇보다 앞서는지만 Comparator로 정했다.

**선택과 배분에 같은 우선순위를 쓴다.** 그래서 취약 영역 주제는 같은 중요도의 다른 주제보다
시간이 덜 깎인다. "취약 영역은 덜 축소한다"는 별도 규칙 없이 그렇게 된다.

**최종 순서는 `topicOrder`를 따른다.** 중요하다고 뒤 내용을 앞으로 끌어오면 선수 개념 없이
공부하게 된다. 중요도는 "무엇을 넣을지"에만 쓴다.

**시간 값 세 개를 분리했다.** `originalEstimatedMinutes` / `allocatedMinutes` /
`actualStudyMinutes`. 합치면 계획 대비 실제를 비교할 수 없고 9단계 재조정의 입력이 사라진다.

**계획을 만든 것과 학습을 시작한 것을 구분했다.** 세션은 `READY`를 유지하고
`currentStepOrder`는 비워 둔다.

**Planner가 스스로 결과를 검증한다.** 규칙으로 만들었으니 깨질 리 없다고 두지 않았다.
시간 총합을 넘기는 계획은 실행 불가능한 일정이고, 조용히 나가면 알아채기 어렵다.

### 검증 기록

| # | 검증 항목 | 방법 | 결과 |
| --- | --- | --- | --- |
| 1 | 컴파일 | `./gradlew compileJava` | 통과 |
| 2 | Planner 단위 테스트 | `./gradlew test --tests "*CurriculumPlannerTest*"` | 통과 (무작위 60회 포함) |
| 3 | 전체 테스트 | `./gradlew test` | 통과 (603 tests, 0 failures) |
| 4 | 실제 PostgreSQL 통합 테스트 | 통합 테스트를 PostgreSQL로 재실행 | 통과 (12건) |
| 5 | 실제 PostgreSQL API E2E | Docker + curl | 통과 |
| 6 | 전체 빌드 | `./gradlew clean build` | 통과 |

E2E에서 확인한 계획 (남은 시간 180분, Topic 5개 총 220분):

```
1 프로세스와 스레드  VERY_HIGH  50/50   [CORE_TOPIC]
2 CPU 스케줄링       VERY_HIGH  45/45   [CORE_TOPIC, PAST_EXAM]
3 가상 메모리        HIGH       43/55   [CORE_TOPIC, WEAK_AREA]
4 파일 시스템        MEDIUM     12/40   []
5 입출력             LOW        30/30   [MUST_STUDY]  mandatory

합계 180 = 남은 시간 180
```

`mustStudy`인 LOW 주제가 권장 시간 전부를 받고, MEDIUM 주제가 12분까지 압축됐다.
정책이 의도대로 동작한다.

### 검증 실패와 조치 기록

| 실패 | 원인 | 조치 |
| --- | --- | --- |
| 계획 조회가 500 | 응답 변환이 트랜잭션 밖에서 일어나는데 `StudyStep.topic`이 지연 로딩 프록시였다. `importance`를 읽는 순간 초기화 실패 | 리포지터리 쿼리에 `left join fetch` 추가. N+1도 함께 해결 |
| 서비스 테스트 2건이 다른 예외를 받음 | 테스트가 명세의 검증 순서(주제 → 시간)와 다른 순서를 기대했다 | 테스트 준비 데이터를 명세 순서에 맞춰 수정 |

첫 번째는 H2 통합 테스트에서 잡혔다. 지연 로딩은 DB 종류와 무관한 문제라 이번에는
PostgreSQL 검증까지 가기 전에 드러났다.

## STEP 8 — 학습 단계 진행 및 실제 학습시간 기록 (완료)

### 수행한 작업

```
1. StudyStep.start / complete + Curriculum / StudySession 상태 전이 메서드
2. StudyStepService — 검증, 상태 전이, 다음 단계 조회
3. StudyStepController — start / complete API
4. 계획 조회 응답에 진행 상태와 진행률 추가
5. 에러 코드 6종
6. 테스트 41건, 문서 갱신
```

스키마 변경이 없다. `started_at` / `completed_at` / `actual_study_minutes` 는
7단계에서 이미 만들어 두었다.

### 핵심 결정

**상태 전이는 엔티티에.** 서비스가 `setStatus` / `setStartedAt` 을 늘어놓으면 규칙이
여러 곳에 흩어진다. `studyStep.start(now)` / `complete(now)` 안에 검증까지 넣었다.
서비스는 순서 / 소유권 / 동시 진행처럼 엔티티 하나로 판단할 수 없는 것만 본다.

**실제 학습시간은 서버가 계산한다.** 요청 본문으로 받지 않는다. 화면 타이머는 표시용이고,
기준은 `startedAt` 과 완료 요청 시각이다. 경과 시간은 올림한다 —
`ChronoUnit.MINUTES` 로 버리면 40초 학습이 0분이 되고, 재조정이 "시간을 쓰지 않았다"고 판단한다.

**남은 시간을 차감하지 않는다.** 실제 학습시간에는 브라우저를 닫아 둔 시간까지 들어간다.
그대로 빼면 시험까지 남은 실제 시간과 어긋난다. 다시 계산하는 것은 9단계의 일이다.

**멱등하게 둔 두 경우.** 이미 진행 중인 단계에 start, 이미 완료한 단계에 complete 는
기존 결과를 돌려준다. 409로 막으면 버튼을 두 번 누른 사용자가 오류 화면을 보고,
다시 계산하면 요청할 때마다 학습시간이 늘어난다.

**`currentStepOrder` 는 진행 중인 단계다.** 완료하면 `null` 로 비운다.
다음 순번을 미리 넣으면 "진행 중"과 "다음 차례"가 구분되지 않는다.

### 검증 기록

| 검증 | 결과 |
| --- | --- |
| `./gradlew test` | 644 tests, 0 failures |
| `./gradlew clean build` | 아래 참고 |
| PostgreSQL 통합 테스트 | 통과 (진행 상태 왕복) |
| PostgreSQL API E2E | 아래 참고 |

E2E (남은 180분 / 5단계 계획):

```
STEP 2 먼저 시작        409 INVALID_STUDY_STEP_ORDER
STEP 1 시작             200 IN_PROGRESS, currentStepOrder=1, 세션/계획 IN_PROGRESS
STEP 1 재시작           200 startedAt 동일
STEP 2 동시 시작        409 ANOTHER_STEP_IN_PROGRESS
STEP 1 완료             200 actual=38분 (37분 전 시작), nextStep=STEP 2
STEP 1 재완료           200 값 불변
STEP 1 재시작           409 STUDY_STEP_ALREADY_COMPLETED
전체 완료               계획 COMPLETED, 세션 IN_PROGRESS, 진행률 100%
시험 시각 경과 후 시작   409 EXAM_ALREADY_STARTED
진행 중 단계 완료        200 (시험이 지나도 완료는 허용)
다른 세션 단계          404 STUDY_STEP_NOT_FOUND
```

`remaining_study_minutes` 180 유지, 남은 단계의 `allocated_minutes` 45/43/12/30 불변.

### 검증 실패와 조치

**무작위 계획 테스트가 실패했다 (7단계 테스트의 결함)** — `@RepeatedTest(60)` 중
2회차에서 `CurriculumGenerationFailedException` 이 났다. 남은 시간 5분에 120분짜리
`VERY_HIGH` 주제 하나뿐이면 최소 72분이 필요해 계획을 만들 수 없다.

이건 정상 동작이다. 무작위 입력 범위에 문서화된 실패 경로가 들어 있는데 테스트가
그것을 예외로 인정하지 않은 것이 문제였다. 운영 코드가 아니라 테스트를 고쳤다.

무작위 입력 테스트를 쓸 때는 입력 공간에 정상적인 실패 경로가 포함되는지 먼저 확인한다.

**통합 테스트에서 시각 비교가 실패했다** — 응답 JSON의 `2026-08-27T20:41:30.7734188` 과
DB에서 다시 읽은 `...773419` 가 달랐다. DB 타임스탬프가 마이크로초까지만 저장한다.

응답 문자열끼리 비교하는 대신 DB 값을 비교하도록 고쳤다.
4단계 `parsedAt` 에서 겪은 것과 같은 원인이다.

### 남은 문제 (이번 단계 범위 밖)

라우팅되지 않는 경로로 POST를 보내면 `NoResourceFoundException` 이 500으로 나간다.
`HttpRequestMethodNotSupportedException` 도 마찬가지다. 404 / 405로 내보내야 한다.
공통 예외 처리 전반에 걸친 변경이라 이번 단계에서 손대지 않았다.

## STEP 4 이후

`docs/` 의 기획 문서와 README의 진행 상태 표를 따른다.
각 단계는 반드시 다음 세 가지를 만족해야 완료로 본다.

- 해당 기능의 단위 테스트가 존재하고 통과한다
- `./gradlew build` 가 성공한다
- API 명세 문서(`docs/api/`)가 실제 구현과 일치한다

---

## 추가 2 — 자료 미업로드 시 일반 지식 기반 생성 (완료)

> STEP 9~11(동적 재조정 / 퀴즈 / 프론트 연동·배포)의 절차 기록은 이 문서에 아직 없다.
> 각 기능의 명세는 `docs/api/` 에, 검증 기록은 `docs/` 의 테스트 문서에 있다.

### 무엇을 만들었나

강의자료를 올리지 않아도 **과목명과 시험 범위만으로** 주제·계획·퀴즈를 만든다.
자료가 손에 없거나 범위만 아는 상황이 실제로 흔한데, 그때 아무것도 못 하면 서비스가 멈춘다.

### 설계 판단

**표시를 감추지 않는 것이 이 기능의 절반이다.** 자료 없이 만든 내용은 실제 수업 범위와
다를 수 있다. 화면이 그 차이를 감추면 사용자는 자기 강의자료에서 뽑은 내용이라고 믿고
그대로 외운다. 그래서 근거를 세션에 남기고(`sourceType`), 커리큘럼·퀴즈 화면에 항상 띄운다.

```
추출된 자료 있음  →  USER_MATERIAL      [📚 학습자료 기반]
추출된 자료 없음  →  GENERAL_KNOWLEDGE  [💡 일반 지식 기반] + 안내 문구
분석 전           →  null               아무것도 그리지 않는다
```

`grounded` 는 `sourceType == USER_MATERIAL` 과 같은 값이지만 서버가 같이 내려준다.
화면이 매번 enum 을 비교하게 두면 비교를 빠뜨린 화면이 하나 생긴다.

### 진행 순서

1. `StudySourceType` enum, `StudySession.examScope` / `sourceType` 추가
2. `AnalysisStateWriter.beginAnalysis` — 자료가 없으면 거절하지 않고 근거를 정한다.
   과목명·시험 범위마저 없을 때만 `NO_PARSED_DOCUMENT`
3. `AiAnalysisClient.generateFromGeneralKnowledge` 추가 (Claude / Gemini / Mock / Unavailable)
4. `AnalysisService` — 자료가 있으면 조각 분석, 없으면 한 번에 생성. 지어낸 출처 문서는 버린다
5. `QuizGenerationService` — 자료가 없으면 Topic 의 제목·요약·핵심 개념을 근거로 삼는다
6. `SessionResponse` / `ExamResponse` 에 필드 노출
7. 프론트 — 시험 범위를 서버로 보내고, 자료 없이 넘어갈 수 있게 하고, 근거를 표시
8. `docs/migrations/001-general-knowledge.sql`, `docs/schema.sql` 갱신
9. 검증 (`scripts/gk-verify.sh`) → `docs/general-knowledge-test.md`

### 겪은 문제

| 문제 | 원인 | 조치 |
| --- | --- | --- |
| 새 이미지가 기동 중 죽음 | 운영 프로파일이 `ddl-auto=validate` 인데 컬럼 추가분을 적용하지 않았다 | `docs/migrations/` 신설. 배포 **전에** 적용하도록 `DEPLOY.md` 에 순서 명시 |
| `PUT /exam` 응답에 시험 범위가 없다 | `ExamResponse` 에 필드를 더하지 않았다. 자료가 없을 땐 그 값이 유일한 근거인데 저장 결과를 확인할 방법이 없었다 | 필드 추가 + 슬라이스/통합 테스트 |
| `NO_PARSED_DOCUMENT` 문구가 자료만 안내 | 빠져나갈 길이 두 개가 됐는데 문구는 하나만 말하고 있었다 | "강의자료를 올리거나, 시험 범위를 입력해 주세요"로 수정 |

### 확인

- `./gradlew test` — 전체 통과
- `scripts/gk-verify.sh` — 19건 전부 통과 (`LLM_MODE=mock`)
- 프론트 `npm test` / `npx tsc --noEmit` / `npm run build` 통과
- 실제 Gemini 호출 검증은 무료 사용량을 아끼기 위해 마지막에 최소 횟수만 따로 한다
