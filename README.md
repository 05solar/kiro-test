# 내일까지 해야 하는데

시험까지 남은 시간과 업로드한 강의자료를 AI가 분석해, 지금 남은 시간 안에서 실제로 수행 가능한
학습 커리큘럼을 만들어 주는 벼락치기 학습 서비스.

## 이 서비스의 핵심 전제

회원가입과 로그인이 없다. 학습을 시작하면 서버가 **8자리 세션 코드**를 발급하고,
사용자는 그 코드만으로 자신의 학습 공간에 접근한다.

```
7K2M9QXF
```

따라서 이 프로젝트의 최상위 도메인은 `User`가 아니라 `StudySession`이다.

```
8자리 Session Code
        ↓
   StudySession
        ↓
    Documents → AI Topics → StudySteps → Quiz → QuizResults
```

세션 코드는 곧 접근 키(Access Key)다. 코드를 아는 사람은 그 학습 공간에 접근할 수 있다.

## 저장소 구조

```
kiro-project
├── backend/          Spring Boot 백엔드
├── frontend/         Next.js 프론트엔드
├── docs/             기획·API·DB 문서
├── scripts/          띄운 스택에 대고 돌리는 검증 스크립트
├── README.md         이 파일
├── PROCESS.md        전체 개발 진행 절차와 단계별 상태
└── AGENT.md          AI 에이전트 작업 규칙
```

프론트엔드는 Next.js(App Router) + React + TypeScript 다. 백엔드와는 같은 오리진의
`/api` 프록시로 통신한다 — 자세한 내용은 [frontend/README.md](frontend/README.md).

## 현재 진행 상태

| 단계 | 내용 | 상태 |
| --- | --- | --- |
| STEP 1 | 프로젝트 기본 구조 | 완료 |
| STEP 2 | 8자리 세션 생성 / 조회 / 복구 | 완료 |
| STEP 3 | 시험 정보 입력 | 완료 |
| STEP 4 | 강의자료 업로드 및 파일 저장 | 완료 |
| STEP 4-2 | PDF/DOCX/TXT 텍스트 추출 | 완료 |
| STEP 5 | 학습 맥락(StudyContext) 입력 | 완료 |
| STEP 6 | AI 문서 분석 및 Topic 생성 | 완료 |
| STEP 7 | 최초 학습 계획(Curriculum) 생성 | 완료 |
| STEP 8 | 학습 단계 진행 및 실제 학습시간 기록 | 완료 |
| STEP 9 | 동적 커리큘럼 재조정 | 완료 |
| STEP 10 | 퀴즈 생성·채점·오답 요약 | 완료 |
| STEP 11 | 프론트엔드 연동 및 Docker 배포 | 완료 |
| 추가 1 | 같은 범위 신규 퀴즈 생성(회차) | 완료 |
| 추가 2 | 자료 미업로드 시 일반 지식 기반 생성 | 완료 |
| 추가 3 | 학습자료 기반 학습 챗봇 | 완료 |

자세한 절차는 [PROCESS.md](PROCESS.md)를 참고한다.

## 빠르게 실행하기

전체를 한 번에 (도커 필요):

```bash
cp .env.example .env    # GEMINI_API_KEY 채우기
docker compose up -d --build
```

브라우저에서 http://localhost 를 연다. 자세한 내용은 [DEPLOY.md](DEPLOY.md).

백엔드만 따로:

```bash
cd backend
./gradlew test          # 테스트 (외부 DB 불필요, H2 사용)
./gradlew build         # 빌드
./gradlew bootRun       # 실행 (PostgreSQL 필요)
```

DB 접속 정보는 환경변수로 주입한다. 자세한 내용은 [backend/README.md](backend/README.md).

## 문서

- [docs/api/session-api.md](docs/api/session-api.md) — 세션 생성 / 조회 / 시험 정보
- [docs/api/document-api.md](docs/api/document-api.md) — 강의자료 업로드 / 목록 / 삭제
- [docs/api/document-parsing-api.md](docs/api/document-parsing-api.md) — 문서 텍스트 추출
- [docs/api/study-context-api.md](docs/api/study-context-api.md) — 학습 맥락 저장 / 조회
- [docs/api/analysis-api.md](docs/api/analysis-api.md) — AI 분석 / Topic 조회
- [docs/api/curriculum-api.md](docs/api/curriculum-api.md) — 학습 계획 생성 / 조회
- [docs/api/study-step-api.md](docs/api/study-step-api.md) — 학습 단계 시작 / 완료
- [docs/api/error-codes.md](docs/api/error-codes.md) — 공통 에러 코드
- [docs/database.md](docs/database.md) — DB 구조
- [docs/backend-anatomy.html](docs/backend-anatomy.html) — 백엔드 동작 원리 (도면·흐름도, 브라우저로 연다)
- [docs/deployment.html](docs/deployment.html) — AWS 배포 점검표

## 알려진 환경 이슈

이 저장소는 `C:\바탕 화면\kiro-project` 처럼 **경로에 한글과 공백**이 들어 있다.
그대로 두면 Gradle 테스트가 전부 `ClassNotFoundException`으로 실패한다.
`backend/gradle.properties`에 해결책과 이유를 적어 두었으니 지우지 말 것.
