# backend 개발 절차

## 기능 하나를 추가하는 순서

이 저장소에서 백엔드 기능은 항상 아래 순서로 만든다. 안쪽(도메인)에서 바깥쪽(API)으로 나온다.

```
1. Entity        도메인 규칙과 상태를 먼저 정한다
2. Enum / Policy 값 제약이 있으면 상수와 규칙을 한 곳에 모은다
3. Repository    조회 조건을 메서드 이름으로 드러낸다
4. Service       유스케이스. 트랜잭션 경계는 여기다
5. DTO           엔티티를 그대로 노출하지 않는다
6. Controller    HTTP 매핑만 한다. 로직을 두지 않는다
7. Validation    잘못된 입력은 DB에 닿기 전에 끊는다
8. Exception     BusinessException 하위로 만들고 ErrorCode를 붙인다
9. Test          단위 → 슬라이스 → 통합
10. Build        ./gradlew build
```

## 테스트 계층

| 계층 | 애노테이션 | 대상 | DB |
| --- | --- | --- | --- |
| 단위 | 없음 / `@ExtendWith(MockitoExtension)` | Generator, Policy, Service | 사용 안 함 |
| 슬라이스(웹) | `@WebMvcTest` | Controller + GlobalExceptionHandler | 사용 안 함 |
| 슬라이스(JPA) | `@DataJpaTest` | Repository, 매핑, 제약조건 | H2 |
| 통합 | `@SpringBootTest(RANDOM_PORT)` | 실제 HTTP 왕복 | H2 |

시간에 의존하는 로직은 `Clock`을 주입해 고정한다.
통합 테스트는 `MutableClock`으로 시간을 앞으로 돌려 만료 연장을 검증한다.

## STEP 1~2 수행 및 검증 기록

### 검증 절차

```bash
./gradlew dependencies --configuration compileClasspath   # 툴체인
./gradlew compileJava                                     # 컴파일
./gradlew test                                            # 테스트
./gradlew clean build                                     # 전체 빌드
```

### 실제 PostgreSQL 연동 검증

테스트는 H2를 쓰므로, 단계 완료 전에 실제 PostgreSQL로 한 번 확인했다.

```bash
docker run -d --name naeil-postgres -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=naeil_study -p 55432:5432 postgres:16-alpine

DB_URL="jdbc:postgresql://localhost:55432/naeil_study" \
DB_USERNAME=postgres DB_PASSWORD=postgres SERVER_PORT=18080 \
java -jar build/libs/study-backend-0.0.1-SNAPSHOT.jar
```

확인한 내용:

| 확인 항목 | 결과 |
| --- | --- |
| `POST /api/sessions` | 201, `{"sessionCode":"LADUPW4C","status":"CREATED"}` |
| `GET /api/sessions/{code}` | 200, 전체 필드 반환 (`remainingStudyMinutes` 포함) |
| 없는 코드 | 404 `SESSION_NOT_FOUND` |
| `ABC`, `abc12345`, `ABCDEFG0`, `12345678!` | 모두 400 `INVALID_SESSION_CODE` |
| 테이블 생성 | `study_sessions` 12개 컬럼, `id`는 `uuid` |
| UNIQUE 제약 | `uk_study_sessions_session_code` 생성됨 |
| status CHECK 제약 | 7개 Enum 값으로 생성됨 |
| `lastAccessedAt` 갱신 | `created_at < last_accessed_at` = true |
| `expiresAt` 연장 | `expires_at - last_accessed_at` = 30일 |

### 겪은 문제와 조치

1. **모든 테스트가 `ClassNotFoundException`**
   한글 경로 + Gradle `@argfile` 인코딩 불일치.
   → `gradle.properties`에 `-Dfile.encoding=MS949` 지정.
   (진단 방법: `./gradlew test --debug | grep "Starting process 'Gradle Test Executor"`
   로 워커 실행 명령을 직접 확인)

2. **세션 코드 문자 집합 테스트 실패**
   명세 자체의 모순. 허용 문자열에는 `L`이 있는데 제외 목록에도 `L`이 있다.
   → 문자열을 정본으로 채택. `../AGENT.md` 8번 항목에 근거를 남김.

3. **`./gradlew clean` 실패**
   셸 작업 디렉터리가 `build/` 하위에 있어서 삭제가 막힘.
   → 프로젝트 루트에서 실행.

## STEP 3 수행 및 검증 기록 (시험 정보 입력)

### 구현

```
1. UpdateExamRequest / ExamResponse DTO (Bean Validation)
2. StudySession.updateExamInfo(subject, examAt, available, remaining, now)
3. SessionService.updateExamInfo + calculateEffectiveStudyMinutes
4. PUT /api/sessions/{sessionCode}/exam
5. INVALID_EXAM_TIME + InvalidExamTimeException
6. GlobalExceptionHandler: 필드 오류 메시지 전달, 본문 파싱 오류 400 처리
```

### 시간 계산 정책

```
minutesUntilExam      = ChronoUnit.MINUTES.between(now, examAt)   초 단위 버림
effectiveStudyMinutes = min(사용자 입력, minutesUntilExam)

availableStudyMinutes = 입력 원본값     진행 중 불변
remainingStudyMinutes = effective       동적 커리큘럼의 기준값
```

`@Future`를 쓰지 않는다. 시스템 시계를 보기 때문에 고정한 `Clock`과 어긋난다.
시각 비교는 서비스에서 하고 `INVALID_EXAM_TIME`으로 응답한다.

### 검증 결과

| 검증 | 결과 |
| --- | --- |
| `./gradlew test` | 173 tests, 0 failures |
| `./gradlew clean build` | BUILD SUCCESSFUL |
| 실제 PostgreSQL E2E | 아래 표 참고 |

| 요청 | 결과 |
| --- | --- |
| 시험까지 240분 / 입력 360분 | 200, available=360, remaining=239 (초 단위 버림) |
| 3일 뒤 시험 / 입력 420분 (수정) | 200, available=420, remaining=420 |
| 과거 시험시간 | 400 `INVALID_EXAM_TIME` |
| 학습시간 0 | 400 `INVALID_REQUEST` "공부 가능한 시간은 1분 이상이어야 합니다." |
| 학습시간 999999 | 400 `INVALID_REQUEST` "10080분(7일) 이하로 입력해 주세요." |
| 없는 세션 | 404 `SESSION_NOT_FOUND` |
| `GET /api/sessions/{code}` | 시험 정보 포함 확인 |

### 겪은 문제와 조치

**E2E에서 한글 과목명 요청이 전부 400** — Git Bash가 `curl -d`의 한글 인자를 MS949로 변환해
전송했다. 앱 로그에 `JSON parse error: Invalid UTF-8 start byte 0xbf`.
요청 본문을 UTF-8 파일로 저장하고 `--data-binary @file` 로 보내 해결했다.
애플리케이션 문제가 아니라 셸 인코딩 문제다. 앞으로 한글이 들어간 요청을 curl로 시험할 때는
반드시 파일로 보낸다.

## STEP 4 수행 및 검증 기록 (강의자료 업로드)

### 구현

```
1. storage 패키지: StorageService 추상화 + LocalStorageService
2. document 패키지: Document, DocumentFileType, DocumentStatus, DocumentPolicy
3. DocumentFileValidator (저장 전 전체 검증)
4. DocumentService (업로드 / 목록 / 삭제 + 보상 처리)
5. DocumentController (POST 201 / GET 200 / DELETE 204)
6. 에러 코드 7종, multipart 예외 처리
```

텍스트 추출은 하지 않았다. 파일은 원본 그대로 저장만 한다.

### 파일 저장 정책

```
저장 경로   {root}/sessions/{sessionId}/documents/{uuid}.{ext}
파일명      originalFileName(표시용) / storedFileName(UUID, 실제)
제한        파일 10개, 개별 20MB, 세션 100MB
형식        PDF, DOCX, TXT (확장자 우선 + MIME Type 보조)
```

### 트랜잭션 경계

파일 시스템은 DB 트랜잭션과 함께 롤백되지 않는다.

```
업로드   전체 검증 → Storage 저장 → saveAllAndFlush
                         ↑                │ 실패
                         └── 보상 삭제 ───┘

삭제     DB 삭제(flush) → Storage 삭제
```

`saveAll` 이 아니라 `saveAllAndFlush` 를 쓴다. 기본 `saveAll` 은 커밋 시점에 반영되어
예외가 서비스 메서드 밖에서 터지고, 그러면 보상 삭제를 할 수 없다.

### 검증 결과

| 검증 | 결과 |
| --- | --- |
| `./gradlew test` | 272 tests, 0 failures |
| `./gradlew clean build` | BUILD SUCCESSFUL |
| 실제 PostgreSQL E2E | 아래 표 참고 |

| 요청 | 결과 |
| --- | --- |
| 파일 3개 업로드 | 201, 세션 상태 `CREATED → UPLOADING` |
| 목록 조회 | 200, 업로드 순서 3건 |
| 한글 파일명 | `운영체제_1주차.pdf` 정상 |
| `../../../etc/passwd.docx` | `passwd.docx` 로 정규화 저장 |
| 다른 세션이 남의 문서 삭제 | 404 `DOCUMENT_NOT_FOUND` |
| 소유 세션 삭제 | 204, DB·파일 모두 제거 |
| 형식 오류 섞인 업로드 | 400, 저장 파일 수 변화 없음 |
| 빈 파일 | 400 `EMPTY_FILE` |
| `documentId` 형식 오류 | 400 `INVALID_REQUEST` |

### 겪은 문제와 조치

1. **`MissingServletRequestPartException` 컴파일 오류** — 패키지가 `org.springframework.web.bind`
   가 아니라 `org.springframework.web.multipart.support` 다.

2. **E2E에서 한글 파일명이 깨져 저장됨** — Git Bash가 `curl -F` 의 파일명 인자를 MS949로
   변환한다(2단계 본문 인코딩과 같은 원인). multipart 본문을 UTF-8 파일로 만들어
   `--data-binary` 로 보내 재검증했다. 앞으로 한글 파일명을 curl로 시험할 때는 이 방법을 쓴다.

## STEP 4-2 수행 및 검증 기록 (텍스트 추출)

### 구현

```
1. 의존성: Apache PDFBox 3.0.8, Apache POI 5.5.1
2. Document: extractedText, characterCount, parsedAt, parseErrorMessage
   + startParsing / markParsed / markParseFailed
3. document/parser: DocumentParser + Pdf/Docx/Txt 구현 + DocumentParserFactory
4. TextNormalizer
5. DocumentParsingService (트랜잭션 없음) + DocumentParseStateWriter (짧은 트랜잭션)
6. POST .../{documentId}/parse, POST .../parse
7. 에러 코드 5종 (409 / 422 계열)
```

### 트랜잭션 경계

```
tx1  beginParsing()    상태를 PARSING 으로 바꾸고 커밋
     ↓
     (밖)              Storage 읽기 + 추출 + 정규화
     ↓
tx2  completeParsing() / failParsing()
```

자기 자신의 메서드를 호출하면 프록시를 거치지 않아 `@Transactional` 이 먹지 않는다.
그래서 상태 변경만 별도 빈으로 분리했다.

### 검증 결과

| 검증 | 결과 |
| --- | --- |
| `./gradlew test` | 342 tests, 0 failures |
| `./gradlew clean build` | BUILD SUCCESSFUL |
| 실제 PostgreSQL E2E | 아래 표 참고 |

| 요청 | 결과 |
| --- | --- |
| TXT + PDF + 손상 PDF 업로드 후 전체 파싱 | 200, 각각 `PARSED` / `PARSED` / `PARSE_FAILED` |
| 정규화 확인 (빈 줄 3개) | DB에 빈 줄 1개로 저장됨 |
| `character_count` vs `length(extracted_text)` | 일치 (80 / 80, 36 / 36) |
| 이미 `PARSED` 인 문서 재요청 | 200, `parsedAt` 그대로 |
| `PARSE_FAILED` 문서 재시도 | 422 (재시도 자체는 막지 않음) |
| 다른 세션이 파싱 시도 | 404 `DOCUMENT_NOT_FOUND` |
| 목록 조회 | `characterCount`, `parsedAt` 포함 |
| 세션 상태 | `UPLOADING` 유지 (`ANALYZING` 아님) |

### 겪은 문제와 조치

**`@Lob String` + PostgreSQL = 본문 대신 OID 저장.**
`extracted_text` 컬럼에 텍스트가 아니라 `16411` 같은 숫자가 들어갔다.
`@Lob` 을 붙이면 PostgreSQL JDBC 드라이버가 값을 large object로 저장하고 컬럼에는
그 OID만 남긴다. **H2에서는 정상 동작해서 342개 테스트가 전부 통과한 상태였다.**
`@Lob` 을 제거하고 `columnDefinition = "TEXT"` 만 남겨 해결했다.

앞으로도 새 엔티티 필드를 추가하면 H2 테스트만 믿지 말고 실제 PostgreSQL로 한 번 확인한다.

## STEP 5 수행 및 검증 기록 (학습 맥락)

### 구현

```
1. StudyContext 엔티티 (StudySession 과 1:0..1, session_id UNIQUE, 단방향 @OneToOne)
2. StudyContextPolicy (정규화 + 2000자 제한)
3. StudyContextRepository (findByStudySessionId)
4. UpdateStudyContextRequest / StudyContextResponse
5. StudyContextService (Upsert + 조회)
6. PUT / GET /api/sessions/{sessionCode}/study-context
```

새 에러 코드를 추가하지 않았다. 기존 `SESSION_NOT_FOUND`, `INVALID_SESSION_CODE`,
`INVALID_REQUEST` 로 필요한 상황이 모두 표현된다.

### 정책

```
저장      Upsert. 세션당 행 하나 (session_id UNIQUE)
PUT       전체 교체. 보내지 않은 항목은 비워진다 → DELETE API 불필요
정규화    앞뒤 공백 제거, 공백만 있으면 null
길이      각 항목 2000자 (@Size, 정규화 전 원본 길이 기준)
미입력 조회 404가 아니라 200 + 전부 null
세션 상태  바꾸지 않는다
```

### 검증 결과

| 검증 | 결과 |
| --- | --- |
| `./gradlew test` | 392 tests, 0 failures |
| `./gradlew clean build` | BUILD SUCCESSFUL |
| 실제 PostgreSQL E2E | 아래 표 참고 |

| 요청 | 결과 |
| --- | --- |
| 미입력 조회 | 200, 전부 null |
| 저장 (앞뒤 공백 / 공백만) | 200, 공백 제거 / null 로 정규화 |
| 수정 (전체 교체) | 200, 보내지 않은 항목 비워짐 |
| 2001자 / 2000자 | 400 / 200 (DB 저장 길이 2000 확인) |
| 없는 세션 PUT·GET | 404 |
| 코드 형식 오류 | 400 |
| 다른 세션 조회 | 전부 null (격리) |
| 세션 상태 | `CREATED` 유지 |
| `study_contexts` 행 개수 | 1 (Upsert가 행을 늘리지 않음) |
| 신규 TEXT 컬럼 4개 | `text` 타입, 본문 그대로 저장 (OID 아님) |

4단계의 `@Lob` 사고를 겪었으므로 신규 TEXT 컬럼은 반드시 실제 PostgreSQL로 확인했다.

## STEP 6 수행 및 검증 기록 (AI 분석)

### 구현

```
1. 의존성: com.anthropic:anthropic-java:2.34.0 (모델 claude-opus-5)
2. topic 패키지: Topic(jsonb 2개), TopicImportance, 조회 API
3. analysis 패키지:
   client      AiAnalysisClient + ClaudeAnalysisClient + Unavailable 대체 구현
   chunk       DocumentChunker (문단 경계 우선, 글자 수 기준)
   prompt      AnalysisPrompts (지시문/데이터 분리, 주입 대비, Grounding)
   validation  AiTopicResponseValidator (실패/보정 두 갈래)
   service     AnalysisService(트랜잭션 없음) + AnalysisStateWriter(짧은 트랜잭션)
4. POST /analysis, GET /topics
5. 에러 코드 4종, SessionStatus 에 ANALYSIS_FAILED 추가
```

### 분석 흐름

```
검증 → ANALYZING → 조각 나누기 → 조각별 1차 분석 → 통합 1회 → 검증 → Topic 교체 → READY
                                                                         ↘ ANALYSIS_FAILED
```

트랜잭션은 `beginAnalysis` / `completeAnalysis` / `failAnalysis` 세 개로 나눴다.
AI 호출은 그 밖에서 한다.

### 검증 결과

| 검증 | 결과 |
| --- | --- |
| `./gradlew test` | 475 tests, 0 failures |
| `./gradlew clean build` | BUILD SUCCESSFUL |
| PostgreSQL API E2E | 400 / 502 / 404 경로 및 스키마 확인 |
| PostgreSQL 통합 테스트 | 14건 통과 (jsonb 왕복 확인) |

4단계의 `@Lob` 사고 이후, 신규 컬럼은 H2 테스트만으로 끝내지 않는다.
이번에는 통합 테스트 자체를 PostgreSQL로 한 번 더 돌려 `jsonb` 쓰기·읽기를 확인했다.

```bash
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:55432/naeil_study" \
SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=postgres \
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver \
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect \
./gradlew test --tests "*AnalysisApiIntegrationTest*"
```

### 겪은 문제와 조치

1. **Fake AI 클라이언트의 요청 기록이 테스트 간 누적** — 스프링 싱글턴이라 그렇다.
   `reset()` 을 추가해 `@BeforeEach` 에서 호출한다.

2. **sed 편집이 테스트 본문의 필요한 줄을 함께 삭제** — 패턴이 여러 곳에 걸렸다.
   여러 파일에 sed를 걸 때는 적용 결과를 반드시 확인한다.

### 알아 둘 것: Enum 값을 늘리면 CHECK 제약이 바뀐다

`SessionStatus` 에 `ANALYSIS_FAILED` 를 더하면서 `study_sessions_status_check` 가 바뀌었다.
`ddl-auto: update` 는 **기존 CHECK 제약을 고치지 않는다.**
이미 데이터가 있는 DB에서는 제약을 직접 지우고 다시 만들어야 한다.

```sql
ALTER TABLE study_sessions DROP CONSTRAINT study_sessions_status_check;
```

새로 만든 DB에서는 문제가 없다. 마이그레이션 도구를 도입해야 할 이유가 하나 더 늘었다.

## STEP 7 수행 및 검증 기록 (최초 학습 계획)

### 구현

```
1. curriculum 패키지: Curriculum, StudyStep + Enum 4종
2. planner: CurriculumPlanner + CurriculumPolicy (규칙 기반)
3. CurriculumService: 검증 + 시험 시간 재확인 + 저장
4. POST / GET /api/sessions/{code}/curriculum
5. 에러 코드 5종
```

AI를 부르지 않는다. 시간 배분은 규칙으로 하고 제약은 코드가 보장한다.

### 계획 알고리즘

```
1. 선택   우선순위 순으로, 최소 시간을 확보할 수 있는 주제만 담는다
2. 배분   남은 시간을 우선순위 순으로 나눠 권장 시간까지 늘린다
3. 정렬   topicOrder 로 되돌리고, 남으면 복습 단계를 붙인다
```

우선순위: `mustStudy → importance → 교수 강조 → 기출 → 취약 → topicOrder`
압축 하한: `VERY_HIGH 60% / HIGH 50% / MEDIUM 30% / LOW 최소 5분`

선택과 배분에 같은 우선순위를 쓰므로 취약 영역이 덜 깎인다. 별도 규칙이 없다.

### 검증 결과

| 검증 | 결과 |
| --- | --- |
| `./gradlew test` | 603 tests, 0 failures |
| `./gradlew clean build` | BUILD SUCCESSFUL |
| PostgreSQL 통합 테스트 | 12건 통과 (jsonb 왕복) |
| PostgreSQL API E2E | 아래 참고 |

E2E (남은 180분 / Topic 5개 220분):

```
1 프로세스와 스레드  VERY_HIGH  50/50
2 CPU 스케줄링       VERY_HIGH  45/45   기출
3 가상 메모리        HIGH       43/55   취약
4 파일 시스템        MEDIUM     12/40
5 입출력             LOW        30/30   mustStudy, mandatory

합계 180 = 남은 시간
```

재요청 시 200 + 기존 계획, 행 수 변화 없음. 세션은 `READY`, `currentStepOrder` 는 null.

### 겪은 문제와 조치

**계획 조회가 500** — 응답 변환은 트랜잭션 밖에서 일어나는데 `StudyStep.topic` 이
지연 로딩 프록시였다. `importance` 를 읽는 순간 초기화가 필요해져 예외가 났다.
리포지터리 쿼리에 `left join fetch` 를 넣어 해결했다. N+1도 함께 사라졌다.

`REVIEW` 단계는 Topic이 없으므로 `left` 를 빼면 복습 단계가 결과에서 조용히 사라진다.

앞으로 응답 DTO 에서 지연 로딩 연관관계를 새로 읽을 때는 쿼리에 페치 조인을 함께 넣는다.

## STEP 8 수행 및 검증 기록 (학습 진행)

### 구현

```
1. 엔티티 상태 전이 메서드 (StudyStep / Curriculum / StudySession)
2. StudyStepService — 검증 + 상태 전이 + 다음 단계
3. POST /steps/{stepId}/start, /complete
4. 계획 조회 응답에 진행 상태 + 진행률
5. 에러 코드 6종
```

스키마를 바꾸지 않았다. 7단계에서 컬럼을 미리 만들어 둔 덕분이다.

### 검증 순서

```
세션 → 계획 → 단계 소유 → 완료 여부 → (진행 중이면 그대로 반환)
  → 시험 시각 → 다른 진행 중 단계 → 순서
```

"다른 진행 중 단계"가 순서보다 앞이다. STEP 1이 진행 중일 때 STEP 2를 시작하면
STEP 2는 첫 번째 `PENDING` 이라 순서 검사를 통과해 버린다.
실제 원인은 동시 진행이므로 그 쪽을 먼저 알려준다.

### 실제 학습시간

```
actualStudyMinutes = (경과초 + 59) / 60
```

서버가 계산한다. 요청 본문으로 받지 않는다.
배정 시간을 넘겨도 자르지 않는다. 초과분이 9단계 재조정의 입력이다.

`remainingStudyMinutes` 는 건드리지 않는다. 브라우저를 닫아 둔 시간까지 들어가는 값이라
그대로 빼면 시험까지 남은 실제 시간과 어긋난다.

### 검증 결과

| 검증 | 결과 |
| --- | --- |
| `./gradlew test` | 644 tests, 0 failures |
| `./gradlew clean build` | 아래 참고 |
| PostgreSQL 통합 테스트 | 통과 |
| PostgreSQL API E2E | 순서 / 동시 진행 / 멱등 / 시험 경과 / 소유권 전부 확인 |

E2E 요약:

```
STEP 1 시작 → 37분 경과 → 완료   actual=38, next=STEP 2
전체 완료                        계획 COMPLETED, 세션 IN_PROGRESS, 100%
remaining_study_minutes          180 유지
남은 단계 allocated_minutes      45/43/12/30 불변
```

### 겪은 문제와 조치

**7단계 무작위 계획 테스트가 실패했다** — 남은 시간 5분에 120분짜리 `VERY_HIGH`
주제 하나뿐이면 최소 72분이 필요해 계획을 만들 수 없다. 이건 정상 동작인데
테스트가 문서화된 실패 경로를 인정하지 않았다. 테스트를 고쳤다.

무작위 입력 테스트는 입력 공간에 정상적인 실패 경로가 들어 있는지 먼저 확인한다.

**시각 비교가 실패했다** — 응답의 `...7734188` 과 DB에서 다시 읽은 `...773419` 가
달랐다. DB 타임스탬프는 마이크로초까지다. DB 값끼리 비교하도록 고쳤다.
4단계 `parsedAt` 과 같은 원인이다.

## 다음 단계 (STEP 9 — 동적 커리큘럼 재조정)

```
1. remainingStudyMinutes 재계산 (현재 시각 + examAt + 완료 기록)
2. 남은 PENDING 단계의 allocatedMinutes 재배분
3. CurriculumPlanner.reallocate(...)
4. 지나치게 긴 실제 학습시간 보정 정책
```

입력은 이미 다 있다.

```java
step.getOriginalEstimatedMinutes();  // 원래 필요했던 시간
step.getAllocatedMinutes();          // 배정한 시간
step.getActualStudyMinutes();        // 실제로 쓴 시간
```

바꾸지 않을 것: `originalEstimatedMinutes`, `availableStudyMinutes`,
`COMPLETED` 단계의 모든 값.
