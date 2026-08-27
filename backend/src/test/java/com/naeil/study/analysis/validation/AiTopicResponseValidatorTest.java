package com.naeil.study.analysis.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.naeil.study.analysis.client.dto.AiDocumentReference;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicResult;
import com.naeil.study.analysis.exception.AiAnalysisException;
import com.naeil.study.topic.entity.TopicImportance;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AiTopicResponseValidator - AI 응답 검증")
class AiTopicResponseValidatorTest {

    private static final UUID DOC_1_ID = UUID.fromString("11111111-0000-4000-8000-000000000001");
    private static final UUID DOC_2_ID = UUID.fromString("11111111-0000-4000-8000-000000000002");

    private final AiTopicResponseValidator validator = new AiTopicResponseValidator(30);

    private final List<AiDocumentReference> documents = List.of(
            new AiDocumentReference("DOC_1", DOC_1_ID, "운영체제_1주차.pdf"),
            new AiDocumentReference("DOC_2", DOC_2_ID, "운영체제_2주차.pdf"));

    private AiTopicResult topic(
            String title, String summary, List<String> keyPoints, String importance,
            Integer minutes, List<String> sources) {
        return new AiTopicResult(title, summary, keyPoints, importance, minutes,
                false, true, false, false, sources);
    }

    private AiTopicResult validTopic() {
        return topic("CPU 스케줄링", "준비 큐에서 CPU를 할당할 프로세스를 결정한다.",
                List.of("FCFS", "SJF", "Round Robin"), "VERY_HIGH", 35, List.of("DOC_1", "DOC_2"));
    }

    private AiTopicAnalysisResult result(AiTopicResult... topics) {
        return new AiTopicAnalysisResult(List.of(topics));
    }

    @Test
    @DisplayName("정상 응답을 검증해 저장 가능한 형태로 돌려준다")
    void acceptsValidResponse() {
        List<ValidatedTopic> validated = validator.validate(result(validTopic()), documents);

        assertThat(validated).hasSize(1);
        ValidatedTopic topic = validated.get(0);
        assertThat(topic.title()).isEqualTo("CPU 스케줄링");
        assertThat(topic.keyPoints()).containsExactly("FCFS", "SJF", "Round Robin");
        assertThat(topic.importance()).isEqualTo(TopicImportance.VERY_HIGH);
        assertThat(topic.estimatedStudyMinutes()).isEqualTo(35);
        assertThat(topic.pastExamMatched()).isTrue();
        assertThat(topic.professorEmphasisMatched()).isFalse();
        assertThat(topic.sourceDocumentIds()).containsExactly(DOC_1_ID, DOC_2_ID);
        assertThat(topic.topicOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 Topic은 배열 순서대로 topicOrder가 매겨진다")
    void assignsTopicOrderInSequence() {
        List<ValidatedTopic> validated = validator.validate(
                result(validTopic(), validTopic(), validTopic()), documents);

        assertThat(validated).extracting(ValidatedTopic::topicOrder).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("topics가 비어 있으면 실패한다")
    void rejectsEmptyTopics() {
        assertThatThrownBy(() -> validator.validate(new AiTopicAnalysisResult(List.of()), documents))
                .isInstanceOf(AiAnalysisException.class);
        assertThatThrownBy(() -> validator.validate(new AiTopicAnalysisResult(null), documents))
                .isInstanceOf(AiAnalysisException.class);
        assertThatThrownBy(() -> validator.validate(null, documents))
                .isInstanceOf(AiAnalysisException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("title이 비어 있으면 실패한다")
    void rejectsBlankTitle(String title) {
        AiTopicResult invalid = topic(title, "요약", List.of("개념"), "HIGH", 30, List.of("DOC_1"));

        assertThatThrownBy(() -> validator.validate(result(invalid), documents))
                .isInstanceOf(AiAnalysisException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("summary가 비어 있으면 실패한다")
    void rejectsBlankSummary(String summary) {
        AiTopicResult invalid = topic("CPU 스케줄링", summary, List.of("개념"), "HIGH", 30, List.of("DOC_1"));

        assertThatThrownBy(() -> validator.validate(result(invalid), documents))
                .isInstanceOf(AiAnalysisException.class);
    }

    @Test
    @DisplayName("keyPoints가 비어 있으면 실패한다")
    void rejectsEmptyKeyPoints() {
        assertThatThrownBy(() -> validator.validate(
                result(topic("제목", "요약", List.of(), "HIGH", 30, List.of("DOC_1"))), documents))
                .isInstanceOf(AiAnalysisException.class);
        assertThatThrownBy(() -> validator.validate(
                result(topic("제목", "요약", null, "HIGH", 30, List.of("DOC_1"))), documents))
                .isInstanceOf(AiAnalysisException.class);
        assertThatThrownBy(() -> validator.validate(
                result(topic("제목", "요약", List.of("  ", ""), "HIGH", 30, List.of("DOC_1"))), documents))
                .isInstanceOf(AiAnalysisException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CRITICAL", "높음", "1", "", "VERY HIGH"})
    @DisplayName("importance가 정해진 값이 아니면 실패한다")
    void rejectsInvalidImportance(String importance) {
        AiTopicResult invalid = topic("제목", "요약", List.of("개념"), importance, 30, List.of("DOC_1"));

        assertThatThrownBy(() -> validator.validate(result(invalid), documents))
                .isInstanceOf(AiAnalysisException.class);
    }

    @Test
    @DisplayName("importance는 소문자로 와도 받아들인다")
    void acceptsLowercaseImportance() {
        AiTopicResult topic = topic("제목", "요약", List.of("개념"), "high", 30, List.of("DOC_1"));

        assertThat(validator.validate(result(topic), documents).get(0).importance())
                .isEqualTo(TopicImportance.HIGH);
    }

    @Test
    @DisplayName("estimatedStudyMinutes가 없으면 실패한다")
    void rejectsMissingStudyMinutes() {
        AiTopicResult invalid = topic("제목", "요약", List.of("개념"), "HIGH", null, List.of("DOC_1"));

        assertThatThrownBy(() -> validator.validate(result(invalid), documents))
                .isInstanceOf(AiAnalysisException.class);
    }

    @Test
    @DisplayName("estimatedStudyMinutes가 범위를 벗어나면 5~120으로 맞춘다")
    void clampsStudyMinutes() {
        AiTopicResult tooShort = topic("제목", "요약", List.of("개념"), "HIGH", 0, List.of("DOC_1"));
        AiTopicResult tooLong = topic("제목", "요약", List.of("개념"), "HIGH", 500, List.of("DOC_1"));

        assertThat(validator.validate(result(tooShort), documents).get(0).estimatedStudyMinutes())
                .isEqualTo(5);
        assertThat(validator.validate(result(tooLong), documents).get(0).estimatedStudyMinutes())
                .isEqualTo(120);
    }

    @Test
    @DisplayName("title이 200자를 넘으면 잘라낸다")
    void truncatesLongTitle() {
        AiTopicResult longTitle = topic("가".repeat(300), "요약", List.of("개념"), "HIGH", 30, List.of("DOC_1"));

        assertThat(validator.validate(result(longTitle), documents).get(0).title()).hasSize(200);
    }

    @Test
    @DisplayName("keyPoints 중복은 순서를 유지하며 제거한다")
    void removesDuplicateKeyPoints() {
        AiTopicResult duplicated = topic("제목", "요약",
                List.of("FCFS", "SJF", "FCFS", " SJF ", "Round Robin"), "HIGH", 30, List.of("DOC_1"));

        assertThat(validator.validate(result(duplicated), documents).get(0).keyPoints())
                .containsExactly("FCFS", "SJF", "Round Robin");
    }

    @Test
    @DisplayName("존재하지 않는 문서 참조는 버린다")
    void dropsUnknownSourceDocuments() {
        AiTopicResult topic = topic("제목", "요약", List.of("개념"), "HIGH", 30,
                List.of("DOC_1", "DOC_99", "완전히 다른 값"));

        assertThat(validator.validate(result(topic), documents).get(0).sourceDocumentIds())
                .containsExactly(DOC_1_ID);
    }

    @Test
    @DisplayName("문서 참조가 전부 잘못돼도 분석 전체를 실패시키지 않는다")
    void keepsTopicWhenAllReferencesAreUnknown() {
        AiTopicResult topic = topic("제목", "요약", List.of("개념"), "HIGH", 30, List.of("DOC_99"));

        List<ValidatedTopic> validated = validator.validate(result(topic), documents);

        assertThat(validated).hasSize(1);
        assertThat(validated.get(0).sourceDocumentIds()).isEmpty();
    }

    @Test
    @DisplayName("문서 참조가 없어도 정상 처리한다")
    void acceptsMissingSourceDocuments() {
        AiTopicResult topic = topic("제목", "요약", List.of("개념"), "HIGH", 30, null);

        assertThat(validator.validate(result(topic), documents).get(0).sourceDocumentIds()).isEmpty();
    }

    @Test
    @DisplayName("matched 값이 없으면 false로 본다")
    void treatsMissingMatchedAsFalse() {
        AiTopicResult topic = new AiTopicResult("제목", "요약", List.of("개념"), "HIGH", 30,
                null, null, null, null, List.of("DOC_1"));

        ValidatedTopic validated = validator.validate(result(topic), documents).get(0);

        assertThat(validated.professorEmphasisMatched()).isFalse();
        assertThat(validated.pastExamMatched()).isFalse();
        assertThat(validated.weakAreaMatched()).isFalse();
        assertThat(validated.mustStudyMatched()).isFalse();
    }

    @Test
    @DisplayName("Topic 수가 상한을 넘으면 앞에서부터 상한까지만 남긴다")
    void truncatesTooManyTopics() {
        AiTopicResponseValidator smallLimit = new AiTopicResponseValidator(3);
        AiTopicResult[] topics = new AiTopicResult[10];
        for (int i = 0; i < topics.length; i++) {
            topics[i] = topic("주제 " + i, "요약", List.of("개념"), "HIGH", 30, List.of("DOC_1"));
        }

        List<ValidatedTopic> validated = smallLimit.validate(result(topics), documents);

        assertThat(validated).hasSize(3);
        assertThat(validated).extracting(ValidatedTopic::title)
                .containsExactly("주제 0", "주제 1", "주제 2");
    }
}
