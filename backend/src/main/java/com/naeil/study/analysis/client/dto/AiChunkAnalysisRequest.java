package com.naeil.study.analysis.client.dto;

/**
 * 강의자료 조각 하나에 대한 1차 분석 요청.
 *
 * <p>이 단계에서는 중요도나 학습시간을 정하지 않는다. 조각 하나만 보고는
 * 전체에서의 우선순위를 판단할 수 없기 때문이다. 주제 후보만 뽑는다.
 */
public record AiChunkAnalysisRequest(
        String subject,
        String documentReference,
        String fileName,
        int chunkIndex,
        int chunkCount,
        String text
) {
}
