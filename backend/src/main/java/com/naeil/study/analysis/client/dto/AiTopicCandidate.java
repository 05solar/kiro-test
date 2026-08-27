package com.naeil.study.analysis.client.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * 1차 분석에서 나온 주제 후보. AI 구조화 응답의 항목이다.
 *
 * <p>중요도, 학습시간, 학습 맥락 일치 여부는 여기 없다. 최종 통합 단계에서 결정한다.
 */
public record AiTopicCandidate(
        @JsonPropertyDescription("주제 이름. 문장이 아니라 이름. 최대 200자")
        String title,

        @JsonPropertyDescription("이 조각에서 이 주제에 해당하는 핵심 내용 요약. 자료에 없는 내용을 추가하지 말 것")
        String summary,

        @JsonPropertyDescription("핵심 개념 목록. 최소 1개")
        List<String> keyPoints
) {
}
