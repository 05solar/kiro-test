package com.naeil.study.analysis.client.dto;

/**
 * 어느 조각에서 나왔는지까지 붙인 주제 후보.
 *
 * <p>최종 통합 단계에서 AI가 출처를 판단할 수 있도록 참조값을 함께 넘긴다.
 */
public record AiSourcedTopicCandidate(
        String documentReference,
        String fileName,
        int chunkIndex,
        AiTopicCandidate candidate
) {
}
