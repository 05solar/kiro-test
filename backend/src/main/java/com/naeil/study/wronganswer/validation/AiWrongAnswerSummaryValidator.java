package com.naeil.study.wronganswer.validation;

import com.naeil.study.wronganswer.client.dto.AiTopicReview;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerSummaryResult;
import com.naeil.study.wronganswer.entity.ReviewPriority;
import com.naeil.study.wronganswer.entity.TopicReviewSnapshot;
import com.naeil.study.wronganswer.exception.WrongAnswerSummaryGenerationFailedException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * AI 오답 요약 응답을 검증하고 저장 가능한 형태로 바꾼다.
 *
 * <p><b>AI 응답을 그대로 DB에 넣지 않는다.</b> 퀴즈 검증기처럼 보정 없이 전부 실패로
 * 처리한다. 절반만 채워진 복습 자료는 "여기까지만 보면 된다"는 잘못된 신호가 된다.
 *
 * <pre>
 * 실패 조건
 *   - topics 가 비었다
 *   - overallReview 가 비었다
 *   - topicReference 가 요청에 없던 값이다 (AI 가 지어낸 참조)
 *   - 같은 Topic 이 두 번 나온다
 *   - wrongConcepts / keyReviewPoints 가 비었다
 *   - summary 가 비었다
 *   - priority 가 VERY_HIGH / HIGH / MEDIUM 이 아니다
 * </pre>
 *
 * <p>Topic 제목은 AI 응답이 아니라 <b>서버가 아는 값</b>을 쓴다. AI 가 제목을 바꿔치기하거나
 * 오타를 내도 화면의 제목은 실제 Topic 과 항상 일치해야 한다.
 */
@Component
public class AiWrongAnswerSummaryValidator {

    /**
     * 검증 결과.
     *
     * @param overallSummary 전체 총평
     * @param topicReviews   Topic 별 복습 (요청에 넣은 Topic 순서)
     */
    public record ValidatedSummary(String overallSummary, List<TopicReviewSnapshot> topicReviews) {
    }

    /**
     * 참조값을 실제 Topic 으로 되돌리며 검증한다.
     *
     * @param titlesByReference 요청에 넣은 참조값 → (topicId, title). 응답은 이 안의 값만 쓸 수 있다
     * @throws WrongAnswerSummaryGenerationFailedException 규칙에 맞지 않는 경우
     */
    public ValidatedSummary validate(
            AiWrongAnswerSummaryResult result,
            Map<String, TopicIdentity> titlesByReference
    ) {
        if (result == null || result.topics() == null || result.topics().isEmpty()) {
            throw new WrongAnswerSummaryGenerationFailedException("ai returned no topic reviews");
        }
        String overall = requireText(result.overallReview(), "overallReview");

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<TopicReviewSnapshot> reviews = new ArrayList<>(result.topics().size());
        for (int i = 0; i < result.topics().size(); i++) {
            AiTopicReview review = result.topics().get(i);
            int order = i + 1;

            String reference = review.topicReference() == null ? null : review.topicReference().strip();
            TopicIdentity identity = titlesByReference.get(reference);
            if (identity == null) {
                throw new WrongAnswerSummaryGenerationFailedException(
                        "unknown topic reference at review " + order + ": " + review.topicReference());
            }
            if (!seen.add(reference)) {
                throw new WrongAnswerSummaryGenerationFailedException(
                        "duplicated topic reference at review " + order + ": " + reference);
            }

            reviews.add(new TopicReviewSnapshot(
                    identity.topicId(),
                    identity.title(),
                    requireTexts(review.wrongConcepts(), "wrongConcepts", order),
                    requireText(review.summary(), "summary at review " + order),
                    requireTexts(review.keyReviewPoints(), "keyReviewPoints", order),
                    ReviewPriority.from(review.priority())
                            .orElseThrow(() -> new WrongAnswerSummaryGenerationFailedException(
                                    "invalid priority at review " + order + ": " + review.priority()))));
        }
        return new ValidatedSummary(overall, List.copyOf(reviews));
    }

    /** 서버가 아는 Topic 의 식별 정보. 참조값 매핑에 쓴다. */
    public record TopicIdentity(UUID topicId, String title) {
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new WrongAnswerSummaryGenerationFailedException("blank " + field);
        }
        return value.strip();
    }

    private List<String> requireTexts(List<String> values, String field, int order) {
        if (values == null || values.isEmpty()) {
            throw new WrongAnswerSummaryGenerationFailedException(
                    "empty " + field + " at review " + order);
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                unique.add(value.strip());
            }
        }
        if (unique.isEmpty()) {
            throw new WrongAnswerSummaryGenerationFailedException(
                    "empty " + field + " at review " + order);
        }
        return List.copyOf(unique);
    }
}
