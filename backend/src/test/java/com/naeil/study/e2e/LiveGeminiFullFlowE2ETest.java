package com.naeil.study.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * 실제 Gemini API 와 실제 강의자료 PDF 로 전체 플로우를 구동하는 라이브 E2E.
 *
 * <p><b>기본 테스트 스위트에서는 실행되지 않는다.</b> 다음 환경변수가 모두 있어야 켜진다.
 * <pre>
 * GEMINI_API_KEY   Gemini API 키
 * E2E_DOC_DIRS     실행 회차별 강의자료 폴더 경로. 세미콜론(;)으로 구분, 회차 수만큼
 * </pre>
 *
 * <p>실행 예 (PowerShell):
 * <pre>
 * $env:GEMINI_API_KEY = "..."; $env:E2E_DOC_DIRS = "C:\a;C:\b;C:\c"
 * ./gradlew test --tests "*LiveGeminiFullFlowE2ETest*"
 * </pre>
 *
 * <p>각 회차는 자기 폴더의 PDF 전부로 다음 흐름을 실제 HTTP 로 수행한다.
 * <pre>
 * 세션 생성 → 시험 정보 → 업로드 → 텍스트 추출 → AI 분석 → 학습 계획
 *   → STEP 1 학습(시작/완료 + 동적 재조정) → 퀴즈 생성 → 답안 5개 → 점수 집계
 *   → 오답 복습 요약 생성/조회
 * </pre>
 *
 * <p>회차마다 진행 과정과 결과를 {@code build/e2e-reports/run-N.md} 로 남긴다.
 * 리포트에 API 키는 절대 쓰지 않는다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ai.provider=gemini",
                // 테스트 application.yml 이 메인 yml 을 대체하므로 환경변수 매핑을 여기서 다시 잇는다
                "ai.gemini.api-key=${GEMINI_API_KEY:}",
                "ai.gemini.model=${GEMINI_MODEL:gemini-3.5-flash-lite}",
                // 무료 티어 분당 요청 제한(429)을 백오프로 넘기기 위해 재시도를 넉넉히 둔다
                "ai.max-retries=6",
                // 테스트 application.yml 에는 multipart 제한이 없어 기본값(1MB)이 적용된다.
                // 실제 강의자료 PDF 는 1MB 를 넘으므로 운영 설정과 같은 값으로 맞춘다.
                "spring.servlet.multipart.max-file-size=20MB",
                "spring.servlet.multipart.max-request-size=100MB",
                // 무료 티어 분당 한도 안에서 끝나도록 청크를 크게 잡아 호출 수를 줄인다.
                // gemini flash 계열은 3만 자 입력을 무리 없이 다룬다.
                "ai.analysis.chunk-size=30000"
        }
)
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "E2E_DOC_DIRS", matches = ".+")
@DisplayName("라이브 E2E - 실제 Gemini + 실제 강의자료 전체 플로우")
class LiveGeminiFullFlowE2ETest {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.local.root-path", () -> storageRoot.toString());
    }

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {
            };
    private static final String[] SUBJECTS = {"운영체제", "소프트웨어공학", "자료구조"};

    @Autowired
    private TestRestTemplate restTemplate;

    private final StringBuilder report = new StringBuilder();
    private long phaseStartedAt;

    @BeforeEach
    void relaxHttpTimeouts() {
        // 무료 티어 429 백오프 때문에 분석 호출이 수 분 걸릴 수 있다.
        // 서버가 일하는 동안 클라이언트가 먼저 끊지 않도록 읽기 타임아웃을 넉넉히 잡는다.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(1_800_000);
        restTemplate.getRestTemplate().setRequestFactory(factory);
    }

    @RepeatedTest(value = 3, name = "전체 플로우 {currentRepetition}/{totalRepetitions}")
    @DisplayName("강의자료 폴더 하나로 전체 플로우를 구동한다")
    void fullFlow(RepetitionInfo repetition) throws Exception {
        int run = repetition.getCurrentRepetition();
        String[] docDirs = System.getenv("E2E_DOC_DIRS").split(";");
        assertThat(docDirs.length).isGreaterThanOrEqualTo(run);
        Path docDir = Path.of(docDirs[run - 1].strip());
        String subject = SUBJECTS[(run - 1) % SUBJECTS.length];

        long runStartedAt = System.currentTimeMillis();
        report.setLength(0);
        report.append("# 라이브 E2E Run ").append(run).append(" — ").append(subject).append('\n')
                .append("\n- 실행 시각: ").append(LocalDateTime.now()).append('\n')
                .append("- 자료 폴더: `").append(docDir).append("`\n")
                .append("- AI: Gemini (`ai.provider=gemini`)\n\n");

        try {
            runFlow(run, subject, docDir);
            report.append("\n## 최종 결과: **성공**\n");
        } catch (AssertionError | RuntimeException e) {
            report.append("\n## 최종 결과: **실패**\n\n```\n")
                    .append(e.getClass().getSimpleName()).append(": ").append(e.getMessage())
                    .append("\n```\n");
            throw e;
        } finally {
            report.append("\n총 소요: ").append(Duration.ofMillis(
                    System.currentTimeMillis() - runStartedAt).toSeconds()).append("초\n");
            writeReport(run);
        }
    }

    private void runFlow(int run, String subject, Path docDir) throws Exception {
        // 1. 세션 생성
        beginPhase("1. 세션 생성");
        ResponseEntity<Map<String, Object>> created =
                restTemplate.exchange("/api/sessions", HttpMethod.POST, null, JSON_MAP);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        String sessionCode = (String) created.getBody().get("sessionCode");
        endPhase("201, sessionCode=" + sessionCode);

        // 2. 시험 정보
        beginPhase("2. 시험 정보 입력");
        String examAt = LocalDateTime.now().plusHours(6)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        ResponseEntity<Map<String, Object>> exam = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/exam", HttpMethod.PUT,
                jsonRequest("""
                        {"subject":"%s","examAt":"%s","availableStudyMinutes":180}
                        """.formatted(subject, examAt)), JSON_MAP);
        assertThat(exam.getStatusCode().value()).isEqualTo(200);
        endPhase("200, 시험까지 6시간 / 학습 가능 180분");

        // 3. 강의자료 업로드 (폴더의 PDF 전부, 최대 10개)
        beginPhase("3. 강의자료 업로드");
        List<Path> pdfs;
        try (var stream = Files.list(docDir)) {
            pdfs = stream.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .sorted()
                    .limit(10)
                    .toList();
        }
        assertThat(pdfs).isNotEmpty();
        MultiValueMap<String, Object> uploadBody = new LinkedMultiValueMap<>();
        for (Path pdf : pdfs) {
            byte[] bytes = Files.readAllBytes(pdf);
            String fileName = pdf.getFileName().toString();
            uploadBody.add("files", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            });
            report.append("  - `").append(fileName).append("` (")
                    .append(bytes.length / 1024).append(" KB)\n");
        }
        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map<String, Object>> uploaded = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/documents", HttpMethod.POST,
                new HttpEntity<>(uploadBody, uploadHeaders), JSON_MAP);
        assertThat(uploaded.getStatusCode().value()).isEqualTo(201);
        endPhase("201, " + pdfs.size() + "개 업로드");

        // 4. 텍스트 추출
        beginPhase("4. 텍스트 추출 (PDFBox)");
        restTemplate.exchange("/api/sessions/" + sessionCode + "/documents/parse",
                HttpMethod.POST, null, JSON_MAP);
        ResponseEntity<Map<String, Object>> documents = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/documents", HttpMethod.GET, null, JSON_MAP);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documentList =
                (List<Map<String, Object>>) documents.getBody().get("documents");
        long parsed = documentList.stream().filter(d -> "PARSED".equals(d.get("status"))).count();
        for (Map<String, Object> document : documentList) {
            report.append("  - ").append(document.get("originalFileName"))
                    .append(" → ").append(document.get("status"))
                    .append(document.get("characterCount") == null
                            ? "" : " (" + document.get("characterCount") + "자)")
                    .append('\n');
        }
        assertThat(parsed).isGreaterThan(0);
        endPhase(parsed + "/" + documentList.size() + "개 PARSED");

        // 5. AI 분석 (Gemini)
        beginPhase("5. AI 분석 (Gemini)");
        ResponseEntity<Map<String, Object>> analysis = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/analysis", HttpMethod.POST, null, JSON_MAP);
        assertThat(analysis.getStatusCode().value())
                .as("analysis: " + analysis.getBody())
                .isEqualTo(200);
        ResponseEntity<Map<String, Object>> topics = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/topics", HttpMethod.GET, null, JSON_MAP);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topicList =
                (List<Map<String, Object>>) topics.getBody().get("topics");
        assertThat(topicList).isNotEmpty();
        for (Map<String, Object> topic : topicList) {
            report.append("  - [").append(topic.get("importance")).append("] ")
                    .append(topic.get("title"))
                    .append(" (권장 ").append(topic.get("estimatedStudyMinutes")).append("분)\n");
        }
        endPhase("Topic " + topicList.size() + "개 생성");

        // 6. 학습 계획
        beginPhase("6. 학습 계획 생성 (규칙 기반, AI 미호출)");
        ResponseEntity<Map<String, Object>> curriculum = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/curriculum", HttpMethod.POST, null, JSON_MAP);
        assertThat(curriculum.getStatusCode().value())
                .as("curriculum: " + curriculum.getBody())
                .isEqualTo(201);
        List<Map<String, Object>> steps = stepsOf(curriculum);
        for (Map<String, Object> step : steps) {
            report.append("  - STEP ").append(step.get("order")).append(" [")
                    .append(step.get("type")).append("] ").append(step.get("title"))
                    .append(" — 배정 ").append(step.get("allocatedMinutes")).append("분\n");
        }
        endPhase("단계 " + steps.size() + "개, 총 배정 "
                + curriculum.getBody().get("totalAllocatedMinutes") + "분");

        // 7. STEP 1 학습 (시작 → 완료 → 동적 재조정)
        beginPhase("7. STEP 1 학습과 동적 재조정");
        Object firstStepId = steps.get(0).get("id");
        String firstTopicId = String.valueOf(steps.get(0).get("topicId"));
        ResponseEntity<Map<String, Object>> started = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/steps/" + firstStepId + "/start",
                HttpMethod.POST, null, JSON_MAP);
        assertThat(started.getStatusCode().value()).isEqualTo(200);
        ResponseEntity<Map<String, Object>> completed = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/steps/" + firstStepId + "/complete",
                HttpMethod.POST, null, JSON_MAP);
        assertThat(completed.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> time = (Map<String, Object>) completed.getBody().get("time");
        @SuppressWarnings("unchecked")
        Map<String, Object> reallocation =
                (Map<String, Object>) completed.getBody().get("reallocation");
        endPhase("완료. 남은 학습시간 " + time.get("remainingStudyMinutes")
                + "분, 재조정 changed=" + reallocation.get("changed"));

        // 8. 퀴즈 생성 (Gemini)
        beginPhase("8. 퀴즈 생성 (Gemini)");
        ResponseEntity<Map<String, Object>> quizzes = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/topics/" + firstTopicId + "/quizzes",
                HttpMethod.POST, null, JSON_MAP);
        assertThat(quizzes.getStatusCode().value())
                .as("quizzes: " + quizzes.getBody())
                .isEqualTo(201);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> quizList =
                (List<Map<String, Object>>) quizzes.getBody().get("quizzes");
        assertThat(quizList).hasSizeBetween(3, 5);
        for (Map<String, Object> quiz : quizList) {
            // 정답 정보가 문제 응답에 절대 없어야 한다
            assertThat(quiz).doesNotContainKeys("correctIndex", "explanation");
            report.append("  - Q").append(quiz.get("order")).append(" [")
                    .append(quiz.get("difficulty")).append("] ")
                    .append(shorten(String.valueOf(quiz.get("question")))).append('\n');
        }
        endPhase(quizList.size() + "문제, 정답 미노출 확인");

        // 9. 답안 제출과 채점
        beginPhase("9. 답안 제출 (보기 0,1,2,3,0 순서로 기계적 선택)");
        int correctCount = 0;
        List<String> answerLines = new ArrayList<>();
        for (int i = 0; i < quizList.size(); i++) {
            int selected = i % 4;
            ResponseEntity<Map<String, Object>> answered = restTemplate.exchange(
                    "/api/sessions/" + sessionCode + "/quizzes/" + quizList.get(i).get("id") + "/answer",
                    HttpMethod.POST, jsonRequest("{\"selectedIndex\":" + selected + "}"), JSON_MAP);
            assertThat(answered.getStatusCode().value()).isEqualTo(200);
            boolean correct = Boolean.TRUE.equals(answered.getBody().get("correct"));
            if (correct) {
                correctCount++;
            }
            answerLines.add("  - Q" + (i + 1) + ": 선택 " + selected
                    + " / 정답 " + answered.getBody().get("correctIndex")
                    + " → " + (correct ? "정답" : "오답"));
        }
        answerLines.forEach(line -> report.append(line).append('\n'));
        endPhase("정답 " + correctCount + "/" + quizList.size());

        // 10. 점수 집계
        beginPhase("10. 점수 집계");
        ResponseEntity<Map<String, Object>> results = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/topics/" + firstTopicId + "/quiz-results",
                HttpMethod.GET, null, JSON_MAP);
        assertThat(results.getStatusCode().value()).isEqualTo(200);
        assertThat(results.getBody().get("completed")).isEqualTo(true);
        assertThat(results.getBody().get("correctAnswers")).isEqualTo(correctCount);
        endPhase("score=" + results.getBody().get("scorePercentage") + "%, completed=true");

        // 11. 오답 복습 요약 (Gemini)
        beginPhase("11. 오답 복습 요약 (Gemini)");
        ResponseEntity<Map<String, Object>> summary = restTemplate.exchange(
                "/api/sessions/" + sessionCode + "/wrong-answer-summary",
                HttpMethod.POST, null, JSON_MAP);
        int wrongCount = quizList.size() - correctCount;
        if (wrongCount == 0) {
            assertThat(summary.getStatusCode().value()).isEqualTo(200);
            assertThat(summary.getBody().get("hasWrongAnswers")).isEqualTo(false);
            endPhase("전부 정답 → AI 미호출, hasWrongAnswers=false (정상)");
        } else {
            assertThat(summary.getStatusCode().value())
                    .as("summary: " + summary.getBody())
                    .isEqualTo(201);
            assertThat(summary.getBody().get("hasWrongAnswers")).isEqualTo(true);
            assertThat(summary.getBody().get("wrongAnswerCount")).isEqualTo(wrongCount);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> summaryTopics =
                    (List<Map<String, Object>>) summary.getBody().get("topics");
            assertThat(summaryTopics).isNotEmpty();
            report.append("  - 총평: ").append(shorten(
                    String.valueOf(summary.getBody().get("overallSummary")))).append('\n');
            for (Map<String, Object> topic : summaryTopics) {
                report.append("  - [").append(topic.get("priority")).append("] ")
                        .append(topic.get("topicTitle"))
                        .append(" — 틀린 개념 ").append(topic.get("wrongConcepts")).append('\n');
            }

            // 조회로 같은 내용이 복구되는지 (재접속 시나리오)
            ResponseEntity<Map<String, Object>> found = restTemplate.exchange(
                    "/api/sessions/" + sessionCode + "/wrong-answer-summary",
                    HttpMethod.GET, null, JSON_MAP);
            assertThat(found.getStatusCode().value()).isEqualTo(200);
            assertThat(found.getBody().get("wrongAnswerCount")).isEqualTo(wrongCount);

            // 답안이 그대로면 재요청 시 캐시를 쓴다 (200)
            ResponseEntity<Map<String, Object>> cached = restTemplate.exchange(
                    "/api/sessions/" + sessionCode + "/wrong-answer-summary",
                    HttpMethod.POST, null, JSON_MAP);
            assertThat(cached.getStatusCode().value()).isEqualTo(200);
            endPhase("오답 " + wrongCount + "건 → 요약 " + summaryTopics.size()
                    + "개 Topic, 조회 복구·캐시 재사용 확인");
        }
    }

    private void beginPhase(String title) {
        phaseStartedAt = System.currentTimeMillis();
        report.append("## ").append(title).append('\n');
    }

    private void endPhase(String outcome) {
        long elapsed = System.currentTimeMillis() - phaseStartedAt;
        report.append("  - 결과: ").append(outcome)
                .append(" (").append(elapsed).append("ms)\n\n");
    }

    private HttpEntity<String> jsonRequest(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        return new HttpEntity<>(body, headers);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> stepsOf(ResponseEntity<Map<String, Object>> response) {
        return (List<Map<String, Object>>) response.getBody().get("steps");
    }

    private String shorten(String text) {
        String single = text.replaceAll("\\s+", " ").strip();
        return single.length() > 80 ? single.substring(0, 80) + "…" : single;
    }

    private void writeReport(int run) throws IOException {
        Path dir = Path.of(System.getenv().getOrDefault("E2E_REPORT_DIR", "build/e2e-reports"));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("run-" + run + ".md"), report.toString(), StandardCharsets.UTF_8);
    }
}
