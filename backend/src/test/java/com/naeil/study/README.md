# 테스트

```
com.naeil.study
├── StudyBackendApplicationTests.java       컨텍스트 로딩
├── document
│   ├── DocumentApiIntegrationTest.java      통합 (업로드/목록/삭제)
│   ├── controller/DocumentControllerTest    웹 슬라이스 (multipart)
│   ├── service/DocumentServiceTest          유스케이스 + 보상 처리
│   ├── entity/DocumentPolicyTest            파일명 규칙 단위
│   ├── repository/DocumentRepositoryTest    JPA 슬라이스
│   ├── validation/DocumentFileValidatorTest 업로드 검증 단위
│   ├── parser/PdfDocumentParserTest         PDF 추출
│   ├── parser/DocxDocumentParserTest        DOCX 추출 (표 포함)
│   ├── parser/TxtDocumentParserTest         TXT 추출 (UTF-8, CP949)
│   ├── parser/TextNormalizerTest            정규화 규칙
│   ├── service/DocumentParsingServiceTest   파싱 상태 전이
│   ├── controller/DocumentParseControllerTest 파싱 API
│   └── DocumentParsingIntegrationTest       통합 (업로드→파싱)
├── analysis
│   ├── AnalysisApiIntegrationTest           통합 (분석 → Topic)
│   ├── controller/AnalysisControllerTest    분석 API
│   ├── service/AnalysisServiceTest          오케스트레이션 + 실패 처리
│   ├── chunk/DocumentChunkerTest            조각 나누기
│   ├── validation/AiTopicResponseValidatorTest  AI 응답 검증
│   └── client/FakeAiAnalysisClient          테스트용 가짜 AI (실제 호출 없음)
├── curriculum
│   ├── StudyStepApiIntegrationTest          통합 (진행/재접속 복구)
│   ├── CurriculumApiIntegrationTest         통합 (계획 생성/조회)
│   ├── controller/CurriculumControllerTest  학습 계획 API
│   ├── controller/StudyStepControllerTest   학습 진행 API
│   ├── service/CurriculumServiceTest        검증 + 저장
│   ├── service/StudyStepServiceTest         상태 전이 + 실제 학습시간
│   ├── planner/CurriculumPlannerTest        시간 배분 (무작위 입력 포함)
│   └── repository/CurriculumRepositoryTest  JPA 슬라이스
├── topic
│   ├── controller/TopicControllerTest       Topic 조회 API
│   └── repository/TopicRepositoryTest       JPA 슬라이스 (jsonb)
├── storage/LocalStorageServiceTest          파일 저장소 (@TempDir)
├── studycontext
│   ├── StudyContextApiIntegrationTest       통합 (저장/수정/조회)
│   ├── controller/StudyContextControllerTest 학습 맥락 API
│   ├── service/StudyContextServiceTest       Upsert 유스케이스
│   ├── entity/StudyContextPolicyTest         정규화 규칙
│   └── repository/StudyContextRepositoryTest JPA 슬라이스 (UNIQUE 제약)
└── session
    ├── SessionApiIntegrationTest.java      통합 (실제 HTTP + DB)
    ├── controller/SessionControllerTest    웹 슬라이스
    ├── service/SessionServiceTest          유스케이스 단위
    ├── service/SessionCodeGeneratorTest    코드 생성 단위
    ├── entity/SessionCodePolicyTest        형식 규칙 단위
    └── repository/StudySessionRepositoryTest  JPA 슬라이스
```

## 계층

| 계층 | 애노테이션 | 대상 | DB |
| --- | --- | --- | --- |
| 단위 | `@ExtendWith(MockitoExtension.class)` 또는 없음 | Policy, Generator, Service | 사용 안 함 |
| 웹 슬라이스 | `@WebMvcTest` | Controller + GlobalExceptionHandler | 사용 안 함 |
| JPA 슬라이스 | `@DataJpaTest` | 매핑, 제약조건, 쿼리 메서드 | H2 |
| 통합 | `@SpringBootTest(RANDOM_PORT)` | 실제 HTTP 왕복 | H2 |

## 외부 의존 없이 돈다

```bash
./gradlew test
```

PostgreSQL도 Docker도 필요 없다. 테스트는 H2 인메모리를 PostgreSQL 호환 모드로 쓴다.
설정은 `src/test/resources/application.yml` 에 있다.

## 시간 다루기

만료 정책이 핵심 로직이므로 시간을 고정한다.

```java
// 단위 테스트
Clock fixedClock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

// 통합 테스트 - 시간을 앞으로 돌려 보관기한 연장을 확인한다
testClock().setNow(CREATED_AT.plusDays(5));
```

`Thread.sleep` 을 쓰지 않는다.

## 현재 규모

```
644 tests, 0 failures
```
