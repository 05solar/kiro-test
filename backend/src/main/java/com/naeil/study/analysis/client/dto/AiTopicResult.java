package com.naeil.study.analysis.client.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * 최종 통합 결과의 Topic 하나. AI 구조화 응답의 항목이다.
 *
 * <p>모든 필드를 감싼 타입(Integer/Boolean)으로 둔다. AI가 값을 빠뜨렸을 때
 * 0이나 false로 조용히 채워지지 않고 검증 단계에서 드러나게 하기 위해서다.
 */
public record AiTopicResult(
        @JsonPropertyDescription("주제 이름. 문장이 아니라 이름. 최대 200자")
        String title,

        @JsonPropertyDescription("시험 직전 이해해야 할 핵심 내용 요약. 강의자료에 없는 사실을 추가하지 말 것")
        String summary,

        @JsonPropertyDescription("핵심 개념 목록. 최소 1개")
        List<String> keyPoints,

        @JsonPropertyDescription("학습 우선순위. VERY_HIGH, HIGH, MEDIUM, LOW 중 하나. 시험 출제 확률이 아님")
        String importance,

        @JsonPropertyDescription("이 주제를 학습하는 데 필요한 예상 시간(분). 5 이상 120 이하")
        Integer estimatedStudyMinutes,

        @JsonPropertyDescription("사용자가 밝힌 교수님 강조 내용과 관련 있으면 true")
        Boolean professorEmphasisMatched,

        @JsonPropertyDescription("사용자가 밝힌 기출/예상 문제와 관련 있으면 true")
        Boolean pastExamMatched,

        @JsonPropertyDescription("사용자가 자신 없다고 밝힌 범위와 관련 있으면 true")
        Boolean weakAreaMatched,

        @JsonPropertyDescription("사용자가 반드시 공부하겠다고 밝힌 범위와 관련 있으면 true")
        Boolean mustStudyMatched,

        @JsonPropertyDescription("이 주제가 나온 문서 참조값 목록. 요청에 제시된 값만 사용할 것")
        List<String> sourceDocuments
) {
}
