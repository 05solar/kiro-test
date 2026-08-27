package com.naeil.study.analysis.client.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** 최종 통합의 구조화 응답. AI는 이 형태로만 답한다. */
public record AiTopicAnalysisResult(
        @JsonPropertyDescription("학습 순서대로 정렬된 최종 주제 목록")
        List<AiTopicResult> topics
) {
}
