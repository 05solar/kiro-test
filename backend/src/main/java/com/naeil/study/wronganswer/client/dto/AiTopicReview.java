package com.naeil.study.wronganswer.client.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * AI 구조화 응답의 Topic 복습 하나.
 */
public record AiTopicReview(
        @JsonPropertyDescription("요청에 제시된 Topic 참조값 (예: TOPIC_1). 새로운 값을 만들지 말 것")
        String topicReference,

        @JsonPropertyDescription("사용자가 틀린 개념 이름 목록. 최소 1개")
        List<String> wrongConcepts,

        @JsonPropertyDescription("틀린 개념 중심의 핵심 복습 설명. 강의자료에서 확인 가능한 내용만")
        String summary,

        @JsonPropertyDescription("반드시 기억할 포인트 목록. 최소 1개")
        List<String> keyReviewPoints,

        @JsonPropertyDescription("복습 우선순위. VERY_HIGH, HIGH, MEDIUM 중 하나")
        String priority
) {
}
