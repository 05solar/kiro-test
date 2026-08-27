package com.naeil.study.analysis.service;

import com.naeil.study.analysis.client.dto.AiStudyContext;
import java.util.List;
import java.util.UUID;

/**
 * 분석에 필요한 정보만 담은 스냅샷.
 *
 * <p>AI 호출은 오래 걸린다. 그 시간 동안 엔티티와 영속성 컨텍스트를 들고 있지 않으려고
 * 트랜잭션 안에서 필요한 값만 복사해 나온다.
 *
 * @param documents 텍스트 추출을 마친 문서만 담는다
 */
public record AnalysisTarget(
        UUID sessionId,
        String subject,
        AiStudyContext studyContext,
        List<AnalysisDocument> documents
) {

    /**
     * 분석 대상 문서 하나.
     *
     * @param reference AI에게 보여줄 참조값 (예: {@code DOC_1})
     */
    public record AnalysisDocument(UUID documentId, String reference, String fileName, String text) {
    }
}
