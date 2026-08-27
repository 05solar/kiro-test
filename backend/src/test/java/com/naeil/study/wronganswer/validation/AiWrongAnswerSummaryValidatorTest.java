package com.naeil.study.wronganswer.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.naeil.study.wronganswer.client.dto.AiTopicReview;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerSummaryResult;
import com.naeil.study.wronganswer.entity.ReviewPriority;
import com.naeil.study.wronganswer.exception.WrongAnswerSummaryGenerationFailedException;
import com.naeil.study.wronganswer.validation.AiWrongAnswerSummaryValidator.TopicIdentity;
import com.naeil.study.wronganswer.validation.AiWrongAnswerSummaryValidator.ValidatedSummary;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AiWrongAnswerSummaryValidator - AI 오답 요약 응답 검증")
class AiWrongAnswerSummaryValidatorTest {

    private static final UUID TOPIC_ID_1 = UUID.fromString("99999999-0000-4000-8000-000000000001");
    private static final UUID TOPIC_ID_2 = UUID.fromString("99999999-0000-4000-8000-000000000002");

    private final AiWrongAnswerSummaryValidator validator = new AiWrongAnswerSummaryValidator();

    private final Map<String, TopicIdentity> identities = Map.of(
            "TOPIC_1", new TopicIdentity(TOPIC_ID_1, "CPU 스케줄링"),
            "TOPIC_2", new TopicIdentity(TOPIC_ID_2, "가상 메모리"));

    private AiTopicReview review(String reference) {
        return new AiTopicReview(reference, List.of("Round Robin"), "핵심 설명",
                List.of("기억할 점"), "HIGH");
    }

    private AiWrongAnswerSummaryResult resultOf(AiTopicReview... reviews) {
        return new AiWrongAnswerSummaryResult("총평", List.of(reviews));
    }

    @Test
    @DisplayName("정상 응답을 검증하고 참조값을 실제 Topic 으로 되돌린다")
    void validatesAndResolvesReferences() {
        ValidatedSummary validated = validator.validate(
                resultOf(review("TOPIC_1"), review("TOPIC_2")), identities);

        assertThat(validated.overallSummary()).isEqualTo("총평");
        assertThat(validated.topicReviews()).hasSize(2);
        assertThat(validated.topicReviews().get(0).topicId()).isEqualTo(TOPIC_ID_1);
        // 제목은 AI 응답이 아니라 서버가 아는 값을 쓴다
        assertThat(validated.topicReviews().get(0).topicTitle()).isEqualTo("CPU 스케줄링");
        assertThat(validated.topicReviews().get(0).priority()).isEqualTo(ReviewPriority.HIGH);
    }

    @Test
    @DisplayName("topics 가 비었으면 실패한다")
    void failsWithoutTopics() {
        assertThatThrownBy(() -> validator.validate(
                new AiWrongAnswerSummaryResult("총평", List.of()), identities))
                .isInstanceOf(WrongAnswerSummaryGenerationFailedException.class);
        assertThatThrownBy(() -> validator.validate(null, identities))
                .isInstanceOf(WrongAnswerSummaryGenerationFailedException.class);
    }

    @Test
    @DisplayName("총평이 비었으면 실패한다")
    void failsWithBlankOverallReview() {
        assertThatThrownBy(() -> validator.validate(
                new AiWrongAnswerSummaryResult("  ", List.of(review("TOPIC_1"))), identities))
                .isInstanceOf(WrongAnswerSummaryGenerationFailedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 Topic 참조는 실패한다")
    void failsWithUnknownReference() {
        assertThatThrownBy(() -> validator.validate(resultOf(review("TOPIC_99")), identities))
                .isInstanceOf(WrongAnswerSummaryGenerationFailedException.class);
    }

    @Test
    @DisplayName("같은 Topic 이 두 번 나오면 실패한다")
    void failsWithDuplicatedReference() {
        assertThatThrownBy(() -> validator.validate(
                resultOf(review("TOPIC_1"), review("TOPIC_1")), identities))
                .isInstanceOf(WrongAnswerSummaryGenerationFailedException.class);
    }

    @Test
    @DisplayName("틀린 개념 목록이 비었으면 실패한다")
    void failsWithEmptyWrongConcepts() {
        AiTopicReview bad = new AiTopicReview("TOPIC_1", List.of(), "설명", List.of("포인트"), "HIGH");

        assertThatThrownBy(() -> validator.validate(resultOf(bad), identities))
                .isInstanceOf(WrongAnswerSummaryGenerationFailedException.class);
    }

    @Test
    @DisplayName("요약이 비었으면 실패한다")
    void failsWithBlankSummary() {
        AiTopicReview bad = new AiTopicReview("TOPIC_1", List.of("개념"), " ", List.of("포인트"), "HIGH");

        assertThatThrownBy(() -> validator.validate(resultOf(bad), identities))
                .isInstanceOf(WrongAnswerSummaryGenerationFailedException.class);
    }

    @Test
    @DisplayName("기억할 포인트가 비었으면 실패한다")
    void failsWithEmptyKeyReviewPoints() {
        AiTopicReview bad = new AiTopicReview("TOPIC_1", List.of("개념"), "설명", List.of("  "), "HIGH");

        assertThatThrownBy(() -> validator.validate(resultOf(bad), identities))
                .isInstanceOf(WrongAnswerSummaryGenerationFailedException.class);
    }

    @Test
    @DisplayName("지원하지 않는 우선순위면 실패한다")
    void failsWithInvalidPriority() {
        AiTopicReview bad = new AiTopicReview("TOPIC_1", List.of("개념"), "설명", List.of("포인트"), "LOW");

        assertThatThrownBy(() -> validator.validate(resultOf(bad), identities))
                .isInstanceOf(WrongAnswerSummaryGenerationFailedException.class);
    }
}
