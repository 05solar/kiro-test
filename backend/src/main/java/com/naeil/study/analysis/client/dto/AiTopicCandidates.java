package com.naeil.study.analysis.client.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** 1차 분석의 구조화 응답. AI는 이 형태로만 답한다. */
public record AiTopicCandidates(
        @JsonPropertyDescription("이 조각에서 찾은 주제 후보 목록. 없으면 빈 배열")
        List<AiTopicCandidate> topics
) {
}
