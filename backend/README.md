# backend

「내일까지 해야 하는데」 백엔드. Spring Boot 3 + Spring Data JPA + PostgreSQL.

## 기술 스택

| 항목 | 버전 |
| --- | --- |
| Java | 21 (Toolchain) |
| Spring Boot | 3.5.16 |
| Gradle | 8.14.3 (Wrapper) |
| DB | PostgreSQL (테스트는 H2 인메모리) |
| 문서 파싱 | Apache PDFBox 3.0.8, Apache POI 5.5.1 |
| AI | Anthropic Java SDK 2.34.0 (모델 claude-opus-5) |
| 기타 | Lombok, Bean Validation |

## 패키지 구조

기능별(도메인별) 패키지 구조를 쓴다. 계층이 아니라 도메인이 먼저다.

```
com.naeil.study
├── StudyBackendApplication.java
│
├── common                     여러 도메인이 함께 쓰는 것만 둔다
│   ├── config                 Clock 등 공통 빈
│   ├── dto                    ErrorResponse
│   └── exception              ErrorCode, BusinessException, GlobalExceptionHandler
│
├── document                   강의자료 도메인
│   ├── controller / service / repository / entity / dto / exception
│   ├── validation             업로드 검증
│   └── parser                 PDF/DOCX/TXT 텍스트 추출 + 정규화
│
├── storage                    파일 저장소 추상화 (StorageService + LocalStorageService)
│
├── analysis                   AI 분석 도메인
│   ├── controller / service / dto / exception
│   ├── client                 AI 호출 추상화 + Claude 구현
│   ├── chunk                  문서 조각 나누기
│   ├── prompt                 프롬프트 조립
│   ├── validation             AI 응답 검증
│   └── config                 AI 클라이언트 설정
│
├── curriculum                 학습 계획 도메인
│   ├── controller / service / repository / entity / dto / exception
│   └── planner                시간 배분 알고리즘
│
├── topic                      학습 단위(Topic) 도메인
│   └── controller / service / repository / entity / dto
│
├── studycontext               학습 맥락 도메인 (사용자 추가 정보)
│   └── controller / service / repository / entity / dto
│
├── quiz                       퀴즈 도메인
│   ├── controller / service / repository / entity / dto / exception
│   ├── client                 AI 퀴즈 호출 추상화 + Claude 구현
│   ├── context                강의자료 관련 구간 추출 (키워드 기반)
│   ├── prompt                 퀴즈 프롬프트 조립
│   ├── validation             AI 퀴즈 응답 검증
│   └── config                 AI 퀴즈 클라이언트 설정
│
├── wronganswer                오답 복습 요약 도메인
│   ├── controller / service / repository / entity / dto / exception
│   ├── client                 AI 요약 호출 추상화 + 구현
│   ├── prompt / validation / config
│
└── session                    학습 세션 도메인
    ├── controller             REST API
    ├── service                유스케이스 + 세션 코드 생성기
    ├── repository             Spring Data JPA
    ├── entity                 StudySession, SessionStatus, SessionCodePolicy
    ├── dto                    요청/응답 record
    └── exception              도메인 예외
```

## 실행

### 테스트 (외부 DB 불필요)

```bash
./gradlew test
```

테스트는 H2 인메모리를 PostgreSQL 호환 모드로 사용한다 (`src/test/resources/application.yml`).

### 빌드

```bash
./gradlew build
```

### 실행 (PostgreSQL 필요)

```bash
# 개발용 DB 띄우기
docker run -d --name naeil-postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=naeil_study \
  -p 5432:5432 postgres:16-alpine

./gradlew bootRun
```

## 환경변수

DB 접속 정보는 하드코딩하지 않는다. 모두 `application.yml`에서 환경변수로 주입받는다.

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/naeil_study` | JDBC URL |
| `DB_USERNAME` | `postgres` | DB 사용자 |
| `DB_PASSWORD` | `postgres` | DB 비밀번호 |
| `SERVER_PORT` | `8080` | 서버 포트 |
| `JPA_DDL_AUTO` | `update` | 운영 전환 시 `validate`로 바꾼다 |
| `JPA_SHOW_SQL` | `true` | SQL 로깅 |
| `SESSION_EXPIRATION_DAYS` | `30` | 마지막 접근 후 보관 일수 |
| `SESSION_CODE_MAX_ATTEMPTS` | `10` | 코드 중복 시 최대 재시도 |
| `LOG_LEVEL` | `DEBUG` | 애플리케이션 로그 레벨 |
| `STORAGE_ROOT_PATH` | `./uploads` | 로컬 Storage 루트 경로 |
| `MAX_FILE_SIZE` | `20MB` | multipart 개별 파일 제한 |
| `MAX_REQUEST_SIZE` | `100MB` | multipart 요청 전체 제한 |
| `AI_PROVIDER` | `anthropic` | AI 공급자 (`anthropic` / `gemini`) |
| `AI_API_KEY` | (없음) | Anthropic 키. 비어 있으면 AI 요청 시점에만 실패한다 |
| `AI_MODEL` | `claude-opus-5` | Anthropic 모델 |
| `GEMINI_API_KEY` | (없음) | `AI_PROVIDER=gemini` 일 때 쓰는 키 |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | Gemini 모델 |
| `AI_TIMEOUT_SECONDS` | `180` | AI 호출 타임아웃 |
| `AI_MAX_RETRIES` | `2` | 연결 오류·5xx 재시도 |
| `AI_CHUNK_SIZE` | `8000` | 문서 조각 크기 (글자 수) |
| `AI_CHUNK_OVERLAP` | `300` | 문서 조각 겹침 (글자 수) |
| `AI_MAX_TOPICS` | `30` | Topic 개수 상한 |
| `CURRICULUM_MIN_TOPIC_MINUTES` | `5` | 학습 단계 하나의 최소 배정 시간 |
| `CURRICULUM_REVIEW_MIN_MINUTES` | `10` | 복습 단계를 만드는 최소 잔여 시간 |
| `CURRICULUM_REVIEW_MAX_MINUTES` | `45` | 복습 단계 최대 시간 |
| `QUIZ_QUESTIONS_PER_TOPIC` | `5` | Topic 하나당 생성할 문제 수 |
| `QUIZ_MAX_CONTEXT_CHARACTERS` | `20000` | 퀴즈 생성 시 AI에 보낼 추출 구간 최대 길이 |
| `WRONG_ANSWER_SUMMARY_MAX_CONTEXT_PER_TOPIC` | `8000` | 오답 요약에서 Topic 하나당 추출 구간 최대 길이 |

기본값은 로컬 개발 편의를 위한 값이다. 배포 환경에서는 반드시 재정의한다.

## API

상세 명세는 [../docs/api/](../docs/api/) 아래 문서를 참고한다.

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/sessions` | 세션 생성, 8자리 코드 발급 |
| GET | `/api/sessions/{sessionCode}` | 세션 조회 + 접근시각/보관기한 갱신 |
| PUT | `/api/sessions/{sessionCode}/exam` | 시험 정보 등록/수정 |
| POST | `/api/sessions/{sessionCode}/documents` | 강의자료 업로드 |
| GET | `/api/sessions/{sessionCode}/documents` | 강의자료 목록 |
| DELETE | `/api/sessions/{sessionCode}/documents/{documentId}` | 강의자료 삭제 |
| POST | `/api/sessions/{sessionCode}/documents/parse` | 전체 문서 텍스트 추출 |
| POST | `/api/sessions/{sessionCode}/documents/{documentId}/parse` | 개별 문서 텍스트 추출 |
| PUT | `/api/sessions/{sessionCode}/study-context` | 학습 맥락 저장/수정 |
| GET | `/api/sessions/{sessionCode}/study-context` | 학습 맥락 조회 |
| POST | `/api/sessions/{sessionCode}/analysis` | AI 분석 실행 |
| GET | `/api/sessions/{sessionCode}/topics` | 분석된 Topic 목록 |
| POST | `/api/sessions/{sessionCode}/curriculum` | 학습 계획 생성 |
| GET | `/api/sessions/{sessionCode}/curriculum` | 학습 계획 조회 (진행 상태 포함) |
| POST | `/api/sessions/{sessionCode}/steps/{stepId}/start` | 학습 단계 시작 |
| POST | `/api/sessions/{sessionCode}/steps/{stepId}/complete` | 학습 단계 완료 + 남은 계획 동적 재조정 |
| POST | `/api/sessions/{sessionCode}/topics/{topicId}/quizzes` | Topic 퀴즈 생성 (멱등) |
| GET | `/api/sessions/{sessionCode}/topics/{topicId}/quizzes` | Topic 퀴즈 조회 (정답 미노출) |
| POST | `/api/sessions/{sessionCode}/quizzes/{quizId}/answer` | 답안 제출 + 채점 |
| GET | `/api/sessions/{sessionCode}/topics/{topicId}/quiz-results` | Topic 점수 집계 |
| POST | `/api/sessions/{sessionCode}/wrong-answer-summary` | 오답 복습 요약 생성 (캐시) |
| GET | `/api/sessions/{sessionCode}/wrong-answer-summary` | 오답 복습 요약 조회 |

## 한글 경로 주의

이 저장소 경로에는 한글과 공백이 들어 있다(`C:\바탕 화면\...`).
Gradle이 테스트 워커에 클래스패스를 `@argfile`로 넘기는데, Gradle은 UTF-8로 쓰고 `java.exe`는
OS 네이티브 인코딩(MS949)으로 읽기 때문에 경로가 깨져 **모든 테스트가 `ClassNotFoundException`으로
실패**한다.

`gradle.properties`의 아래 설정이 이를 막는다. 지우지 말 것.

```properties
org.gradle.jvmargs=-Dfile.encoding=MS949 -Xmx1g
```

프로젝트를 ASCII 경로로 옮기면 이 설정은 필요 없다.
빌드 산출물의 인코딩은 `build.gradle`에서 별도로 UTF-8로 고정한다.
