package com.naeil.study.analysis.client.dto;

import java.util.List;

/**
 * 최종 통합 요청.
 *
 * <p>여러 조각에서 나온 후보를 하나의 Topic 목록으로 합친다. 이 단계에서
 * 중복 제거, 제목 통일, 중요도, 학습시간, 학습 맥락 일치 여부, 기본 순서를 결정한다.
 *
 * @param maxTopics 만들 수 있는 Topic 수의 상한
 */
public record AiTopicMergeRequest(
        String subject,
        AiStudyContext studyContext,
        List<AiDocumentReference> documents,
        List<AiSourcedTopicCandidate> candidates,
        int maxTopics
) {
}
