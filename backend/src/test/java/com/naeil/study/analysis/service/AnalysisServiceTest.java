package com.naeil.study.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naeil.study.analysis.client.FakeAiAnalysisClient;
import com.naeil.study.analysis.client.dto.AiStudyContext;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicCandidates;
import com.naeil.study.analysis.client.dto.AiTopicMergeRequest;
import com.naeil.study.analysis.client.dto.AiTopicResult;
import com.naeil.study.analysis.chunk.DocumentChunker;
import com.naeil.study.analysis.exception.AiAnalysisException;
import com.naeil.study.analysis.exception.AnalysisAlreadyRunningException;
import com.naeil.study.analysis.exception.ExamInfoRequiredException;
import com.naeil.study.analysis.exception.NoParsedDocumentException;
import com.naeil.study.analysis.service.AnalysisTarget.AnalysisDocument;
import com.naeil.study.analysis.validation.AiTopicResponseValidator;
import com.naeil.study.analysis.validation.ValidatedTopic;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.topic.entity.Topic;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisService - AI 강의자료 분석")
class AnalysisServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 18, 0, 0);
    private static final UUID SESSION_ID = UUID.fromString("6f79a1b2-0000-4000-8000-000000000001");
    private static final UUID DOC_1_ID = UUID.fromString("11111111-0000-4000-8000-000000000001");
    private static final UUID DOC_2_ID = UUID.fromString("11111111-0000-4000-8000-000000000002");
    private static final String SESSION_CODE = "7K2M9QXF";

    @Mock
    private SessionService sessionService;

    @Mock
    private AnalysisStateWriter stateWriter;

    @Captor
    private ArgumentCaptor<List<ValidatedTopic>> topicsCaptor;

    private FakeAiAnalysisClient aiClient;
    private AnalysisService analysisService;
    private StudySession session;

    @BeforeEach
    void setUp() throws Exception {
        aiClient = new FakeAiAnalysisClient();
        analysisService = new AnalysisService(
                sessionService,
                stateWriter,
                new DocumentChunker(8000, 300),
                aiClient,
                new AiTopicResponseValidator(30),
                30);
        session = StudySession.create(SESSION_CODE, NOW.minusHours(1), 30L);
        Field field = StudySession.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(session, SESSION_ID);
    }

    private void givenSessionFound() {
        given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
    }

    private AnalysisTarget target(AiStudyContext context, AnalysisDocument... documents) {
        return new AnalysisTarget(SESSION_ID, "운영체제", "3장 ~ 5장",
                com.naeil.study.session.entity.StudySourceType.USER_MATERIAL,
                context, List.of(documents));
    }

    private AnalysisDocument document(UUID id, String reference, String fileName, String text) {
        return new AnalysisDocument(id, reference, fileName, text);
    }

    private void givenAnalysisTarget(AnalysisTarget target) {
        given(stateWriter.beginAnalysis(SESSION_ID)).willReturn(target);
    }

    private void givenSaveSucceeds() {
        given(stateWriter.completeAnalysis(any(UUID.class), anyList())).willReturn(List.of());
    }

    @Test
    @DisplayName("PARSED 문서를 분석해 Topic을 저장한다")
    void analyzesParsedDocuments() {
        givenSessionFound();
        givenAnalysisTarget(target(AiStudyContext.empty(),
                document(DOC_1_ID, "DOC_1", "운영체제_1주차.pdf", "프로세스는 실행 중인 프로그램이다.")));
        givenSaveSucceeds();

        analysisService.analyze(SESSION_CODE);

        verify(stateWriter).completeAnalysis(org.mockito.ArgumentMatchers.eq(SESSION_ID), topicsCaptor.capture());
        List<ValidatedTopic> saved = topicsCaptor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).title()).isEqualTo("CPU 스케줄링");
        assertThat(saved.get(0).sourceDocumentIds()).containsExactly(DOC_1_ID);
        verify(stateWriter, never()).failAnalysis(any(UUID.class));
    }

    @Test
    @DisplayName("여러 문서를 모두 분석 대상으로 넣는다")
    void analyzesMultipleDocuments() {
        givenSessionFound();
        givenAnalysisTarget(target(AiStudyContext.empty(),
                document(DOC_1_ID, "DOC_1", "1주차.pdf", "프로세스와 스레드"),
                document(DOC_2_ID, "DOC_2", "2주차.pdf", "CPU 스케줄링")));
        givenSaveSucceeds();

        analysisService.analyze(SESSION_CODE);

        assertThat(aiClient.chunkRequests()).hasSize(2);
        assertThat(aiClient.chunkRequests()).extracting(request -> request.documentReference())
                .containsExactly("DOC_1", "DOC_2");
        assertThat(aiClient.mergeRequests().get(0).documents())
                .extracting(reference -> reference.reference())
                .containsExactly("DOC_1", "DOC_2");
    }

    @Test
    @DisplayName("긴 문서는 여러 조각으로 나눠 호출한다")
    void splitsLongDocumentIntoChunks() {
        AnalysisService serviceWithSmallChunks = new AnalysisService(
                sessionService, stateWriter, new DocumentChunker(100, 0),
                aiClient, new AiTopicResponseValidator(30), 30);
        givenSessionFound();
        givenAnalysisTarget(target(AiStudyContext.empty(),
                document(DOC_1_ID, "DOC_1", "긴자료.pdf", "가".repeat(500))));
        givenSaveSucceeds();

        serviceWithSmallChunks.analyze(SESSION_CODE);

        assertThat(aiClient.chunkRequests()).hasSize(5);
        assertThat(aiClient.chunkRequests()).extracting(request -> request.chunkIndex())
                .containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    @DisplayName("학습 맥락을 AI 요청에 담는다")
    void includesStudyContext() {
        givenSessionFound();
        AiStudyContext context = new AiStudyContext("교착상태 강조", "CPU 스케줄링 기출", "가상 메모리", "교착상태");
        givenAnalysisTarget(target(context, document(DOC_1_ID, "DOC_1", "자료.pdf", "내용")));
        givenSaveSucceeds();

        analysisService.analyze(SESSION_CODE);

        AiTopicMergeRequest mergeRequest = aiClient.mergeRequests().get(0);
        assertThat(mergeRequest.studyContext()).isEqualTo(context);
        assertThat(mergeRequest.subject()).isEqualTo("운영체제");
    }

    @Test
    @DisplayName("학습 맥락이 없어도 분석은 정상 진행된다")
    void analyzesWithoutStudyContext() {
        givenSessionFound();
        givenAnalysisTarget(target(AiStudyContext.empty(), document(DOC_1_ID, "DOC_1", "자료.pdf", "내용")));
        givenSaveSucceeds();

        analysisService.analyze(SESSION_CODE);

        assertThat(aiClient.mergeRequests().get(0).studyContext().isEmpty()).isTrue();
        verify(stateWriter).completeAnalysis(any(UUID.class), anyList());
    }

    @Test
    @DisplayName("시험 정보가 없으면 분석하지 않는다")
    void failsWithoutExamInfo() {
        givenSessionFound();
        willThrow(new ExamInfoRequiredException()).given(stateWriter).beginAnalysis(SESSION_ID);

        assertThatThrownBy(() -> analysisService.analyze(SESSION_CODE))
                .isInstanceOf(ExamInfoRequiredException.class);

        assertThat(aiClient.chunkRequests()).isEmpty();
        verify(stateWriter, never()).failAnalysis(any(UUID.class));
    }

    @Test
    @DisplayName("PARSED 문서가 없으면 분석하지 않는다")
    void failsWithoutParsedDocument() {
        givenSessionFound();
        willThrow(new NoParsedDocumentException()).given(stateWriter).beginAnalysis(SESSION_ID);

        assertThatThrownBy(() -> analysisService.analyze(SESSION_CODE))
                .isInstanceOf(NoParsedDocumentException.class);

        assertThat(aiClient.chunkRequests()).isEmpty();
    }

    @Test
    @DisplayName("이미 분석 중이면 409가 발생한다")
    void failsWhenAlreadyAnalyzing() {
        givenSessionFound();
        willThrow(new AnalysisAlreadyRunningException()).given(stateWriter).beginAnalysis(SESSION_ID);

        assertThatThrownBy(() -> analysisService.analyze(SESSION_CODE))
                .isInstanceOf(AnalysisAlreadyRunningException.class);

        verify(stateWriter, never()).failAnalysis(any(UUID.class));
    }

    @Test
    @DisplayName("존재하지 않는 세션이면 404가 발생한다")
    void failsWhenSessionNotFound() {
        given(sessionService.getSessionAndTouch("ZZZZZZZZ")).willThrow(new SessionNotFoundException());

        assertThatThrownBy(() -> analysisService.analyze("ZZZZZZZZ"))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("AI 호출이 실패하면 ANALYSIS_FAILED로 기록한다")
    void marksFailedWhenAiCallFails() {
        givenSessionFound();
        givenAnalysisTarget(target(AiStudyContext.empty(), document(DOC_1_ID, "DOC_1", "자료.pdf", "내용")));
        aiClient.failChunkAnalysis(new AiAnalysisException("ai call failed"));

        assertThatThrownBy(() -> analysisService.analyze(SESSION_CODE))
                .isInstanceOf(AiAnalysisException.class);

        verify(stateWriter).failAnalysis(SESSION_ID);
        verify(stateWriter, never()).completeAnalysis(any(UUID.class), anyList());
    }

    @Test
    @DisplayName("응답 검증에 실패하면 한 번 다시 요청한다")
    void retriesMergeOnceWhenValidationFails() {
        givenSessionFound();
        givenAnalysisTarget(target(AiStudyContext.empty(), document(DOC_1_ID, "DOC_1", "자료.pdf", "내용")));
        givenSaveSucceeds();
        java.util.concurrent.atomic.AtomicInteger call = new java.util.concurrent.atomic.AtomicInteger();
        aiClient.respondMergeWith(() -> call.getAndIncrement() == 0
                ? new AiTopicAnalysisResult(List.of())
                : FakeAiAnalysisClient.defaultResult());

        analysisService.analyze(SESSION_CODE);

        assertThat(aiClient.mergeCallCount()).isEqualTo(2);
        verify(stateWriter).completeAnalysis(any(UUID.class), anyList());
    }

    @Test
    @DisplayName("재요청도 검증에 실패하면 ANALYSIS_FAILED로 기록한다")
    void marksFailedWhenRetryAlsoFails() {
        givenSessionFound();
        givenAnalysisTarget(target(AiStudyContext.empty(), document(DOC_1_ID, "DOC_1", "자료.pdf", "내용")));
        aiClient.respondMergeWith(() -> new AiTopicAnalysisResult(List.of()));

        assertThatThrownBy(() -> analysisService.analyze(SESSION_CODE))
                .isInstanceOf(AiAnalysisException.class);

        assertThat(aiClient.mergeCallCount()).isEqualTo(2);
        verify(stateWriter).failAnalysis(SESSION_ID);
    }

    @Test
    @DisplayName("잘못된 importance가 오면 재시도 후에도 실패한다")
    void marksFailedForInvalidImportance() {
        givenSessionFound();
        givenAnalysisTarget(target(AiStudyContext.empty(), document(DOC_1_ID, "DOC_1", "자료.pdf", "내용")));
        aiClient.respondMergeWith(() -> new AiTopicAnalysisResult(List.of(new AiTopicResult(
                "제목", "요약", List.of("개념"), "CRITICAL", 30,
                false, false, false, false, List.of("DOC_1")))));

        assertThatThrownBy(() -> analysisService.analyze(SESSION_CODE))
                .isInstanceOf(AiAnalysisException.class);

        verify(stateWriter).failAnalysis(SESSION_ID);
    }

    @Test
    @DisplayName("주제 후보를 하나도 찾지 못하면 실패한다")
    void marksFailedWhenNoCandidates() {
        givenSessionFound();
        givenAnalysisTarget(target(AiStudyContext.empty(), document(DOC_1_ID, "DOC_1", "목차.pdf", "1 2 3")));
        aiClient.respondChunkWith(() -> new AiTopicCandidates(List.of()));

        assertThatThrownBy(() -> analysisService.analyze(SESSION_CODE))
                .isInstanceOf(AiAnalysisException.class);

        assertThat(aiClient.mergeCallCount()).isZero();
        verify(stateWriter).failAnalysis(SESSION_ID);
    }

    @Test
    @DisplayName("분석 요청은 세션 접근으로 기록된다")
    void refreshesSessionAccessTime() {
        givenSessionFound();
        givenAnalysisTarget(target(AiStudyContext.empty(), document(DOC_1_ID, "DOC_1", "자료.pdf", "내용")));
        givenSaveSucceeds();

        analysisService.analyze(SESSION_CODE);

        verify(sessionService).getSessionAndTouch(SESSION_CODE);
    }

    @Test
    @DisplayName("Topic 예상시간 합이 남은 학습시간을 넘어도 그대로 저장한다")
    void keepsTopicsEvenWhenTotalTimeExceedsRemaining() {
        givenSessionFound();
        givenAnalysisTarget(target(AiStudyContext.empty(), document(DOC_1_ID, "DOC_1", "자료.pdf", "내용")));
        givenSaveSucceeds();
        aiClient.respondMergeWith(() -> new AiTopicAnalysisResult(List.of(
                new AiTopicResult("주제1", "요약", List.of("개념"), "VERY_HIGH", 120,
                        false, false, false, false, List.of("DOC_1")),
                new AiTopicResult("주제2", "요약", List.of("개념"), "VERY_HIGH", 120,
                        false, false, false, false, List.of("DOC_1")),
                new AiTopicResult("주제3", "요약", List.of("개념"), "HIGH", 120,
                        false, false, false, false, List.of("DOC_1")))));

        analysisService.analyze(SESSION_CODE);

        verify(stateWriter).completeAnalysis(org.mockito.ArgumentMatchers.eq(SESSION_ID), topicsCaptor.capture());
        assertThat(topicsCaptor.getValue()).hasSize(3);
        assertThat(topicsCaptor.getValue().stream()
                .mapToInt(ValidatedTopic::estimatedStudyMinutes).sum()).isEqualTo(360);
    }

    @Test
    @DisplayName("Topic 저장에 실패하면 ANALYSIS_FAILED로 기록한다")
    void marksFailedWhenSaveFails() {
        givenSessionFound();
        givenAnalysisTarget(target(AiStudyContext.empty(), document(DOC_1_ID, "DOC_1", "자료.pdf", "내용")));
        willThrow(new IllegalStateException("db down"))
                .given(stateWriter).completeAnalysis(any(UUID.class), anyList());

        assertThatThrownBy(() -> analysisService.analyze(SESSION_CODE))
                .isInstanceOf(IllegalStateException.class);

        verify(stateWriter).failAnalysis(SESSION_ID);
    }

    @Test
    @DisplayName("프롬프트에 넣는 문서 참조는 DOC_n 형식이며 내부 UUID를 노출하지 않는다")
    void usesSafeDocumentReferences() {
        givenSessionFound();
        givenAnalysisTarget(target(AiStudyContext.empty(),
                document(DOC_1_ID, "DOC_1", "자료.pdf", "내용")));
        givenSaveSucceeds();

        analysisService.analyze(SESSION_CODE);

        assertThat(aiClient.chunkRequests().get(0).documentReference()).isEqualTo("DOC_1");
        assertThat(aiClient.chunkRequests().get(0).text()).doesNotContain(DOC_1_ID.toString());
    }

    /**
     * 강의자료 없이 과목명과 시험 범위만으로 만드는 경로.
     *
     * <p>자료를 준비하지 못한 사용자에게도 학습 순서를 줄 수 있어야 한다.
     * 다만 무엇에 근거했는지가 분명해야 한다.
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("자료가 없는 경우")
    class GeneralKnowledge {

        private AnalysisTarget generalKnowledgeTarget() {
            return new AnalysisTarget(
                    SESSION_ID,
                    "자료구조",
                    "정렬부터 이진트리까지",
                    com.naeil.study.session.entity.StudySourceType.GENERAL_KNOWLEDGE,
                    AiStudyContext.empty(),
                    List.of());
        }

        @Test
        @DisplayName("과목명과 시험 범위로 주제를 만든다")
        void generatesFromSubjectAndScope() {
            givenSessionFound();
            givenAnalysisTarget(generalKnowledgeTarget());
            givenSaveSucceeds();

            analysisService.analyze(SESSION_CODE);

            assertThat(aiClient.generalKnowledgeRequests()).hasSize(1);
            var request = aiClient.generalKnowledgeRequests().get(0);
            assertThat(request.subject()).isEqualTo("자료구조");
            assertThat(request.examScope()).isEqualTo("정렬부터 이진트리까지");
        }

        @Test
        @DisplayName("조각 분석을 하지 않는다 — 나눌 자료가 없다")
        void skipsChunkAnalysis() {
            // 자료 기반은 조각 수만큼 AI 를 부른다. 이 경로는 한 번이다.
            givenSessionFound();
            givenAnalysisTarget(generalKnowledgeTarget());
            givenSaveSucceeds();

            analysisService.analyze(SESSION_CODE);

            assertThat(aiClient.chunkRequests()).isEmpty();
            assertThat(aiClient.mergeRequests()).isEmpty();
        }

        @Test
        @DisplayName("AI 가 출처 문서를 지어내도 버린다")
        void discardsInventedSourceDocuments() {
            // 자료가 없는데 출처가 붙어 있으면 그건 지어낸 것이다.
            givenSessionFound();
            givenAnalysisTarget(generalKnowledgeTarget());
            givenSaveSucceeds();

            analysisService.analyze(SESSION_CODE);

            verify(stateWriter).completeAnalysis(
                    org.mockito.ArgumentMatchers.eq(SESSION_ID), topicsCaptor.capture());
            assertThat(topicsCaptor.getValue())
                    .allSatisfy(topic -> assertThat(topic.sourceDocumentIds()).isEmpty());
        }
    }
}
