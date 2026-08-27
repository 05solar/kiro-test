package com.naeil.study.wronganswer.dto;

import com.naeil.study.wronganswer.entity.ReviewPriority;
import com.naeil.study.wronganswer.entity.TopicReviewSnapshot;
import com.naeil.study.wronganswer.entity.WrongAnswerSummary;
import com.naeil.study.wronganswer.service.WrongAnswerSummaryService.SummaryOutcome;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 오답 복습 요약 응답.
 *
 * <p>오답이 없으면({@code hasWrongAnswers = false}) 나머지 값이 비어 있다.
 * 그 상황은 오류가 아니라 "복습할 오답이 없다"는 정상 결과다.
 */
public record WrongAnswerSummaryResponse(
        boolean hasWrongAnswers,
        int wrongAnswerCount,
        LocalDateTime generatedAt,
        String overallSummary,
        List<TopicReviewResponse> topics
) {

    public record TopicReviewResponse(
            UUID topicId,
            String topicTitle,
            List<String> wrongConcepts,
            String summary,
            List<String> keyReviewPoints,
            ReviewPriority priority
    ) {

        static TopicReviewResponse from(TopicReviewSnapshot snapshot) {
            return new TopicReviewResponse(
                    snapshot.topicId(),
                    snapshot.topicTitle(),
                    snapshot.wrongConcepts(),
                    snapshot.summary(),
                    snapshot.keyReviewPoints(),
                    snapshot.priority());
        }
    }

    public static WrongAnswerSummaryResponse from(SummaryOutcome outcome) {
        if (!outcome.hasWrongAnswers()) {
            return new WrongAnswerSummaryResponse(false, 0, null, null, List.of());
        }
        WrongAnswerSummary summary = outcome.summary();
        return new WrongAnswerSummaryResponse(
                true,
                summary.getWrongAnswerCount(),
                summary.getGeneratedAt(),
                summary.getOverallSummary(),
                summary.getTopicReviews().stream().map(TopicReviewResponse::from).toList());
    }
}
