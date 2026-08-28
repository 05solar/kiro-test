package com.naeil.study.analysis.client.dto;

/**
 * 강의자료 없이 학습 주제를 만들 때의 요청.
 *
 * <p>근거가 되는 것은 과목명과 시험 범위뿐이다. 문서 조각도, 출처 참조도 없다.
 * 그래서 {@link AiChunkAnalysisRequest} 나 {@link AiTopicMergeRequest} 와
 * 아예 다른 요청으로 둔다. 하나에 억지로 합치면 "자료가 있을 때만 채워지는 필드"가
 * 늘어나 어느 경로인지 읽기 어려워진다.
 *
 * @param examScope 시험 범위. 사용자가 적은 그대로다. 이것이 유일한 범위 근거다
 * @param maxTopics 주제 수 상한
 */
public record AiGeneralKnowledgeRequest(
        String subject,
        String examScope,
        AiStudyContext studyContext,
        int maxTopics
) {
}
