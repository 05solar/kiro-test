package com.naeil.study.wronganswer.client.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * AI 오답 요약의 구조화 응답 전체.
 */
public record AiWrongAnswerSummaryResult(
        @JsonPropertyDescription("전체 총평. 어떤 영역을 우선 복습할지 한두 문장")
        String overallReview,

        @JsonPropertyDescription("오답이 있었던 Topic 별 복습 요약. 오답과 무관한 Topic 은 포함하지 말 것")
        List<AiTopicReview> topics
) {
}
