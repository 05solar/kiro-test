package com.naeil.study.analysis.client;

import com.naeil.study.analysis.client.dto.AiChunkAnalysisRequest;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicCandidates;
import com.naeil.study.analysis.client.dto.AiTopicMergeRequest;
import com.naeil.study.analysis.exception.AiAnalysisException;

/**
 * API 키가 설정되지 않았을 때 등록되는 클라이언트.
 *
 * <p>키가 없다고 애플리케이션이 뜨지 않으면 분석과 무관한 기능까지 막힌다.
 * 세션, 업로드, 파싱은 AI 없이도 동작해야 하므로 기동은 허용하고,
 * 분석을 실제로 요청한 순간에만 실패시킨다.
 */
public class UnavailableAiAnalysisClient implements AiAnalysisClient {

    @Override
    public AiTopicCandidates analyzeChunk(AiChunkAnalysisRequest request) {
        throw notConfigured();
    }

    @Override
    public AiTopicAnalysisResult mergeTopics(AiTopicMergeRequest request) {
        throw notConfigured();
    }

    private AiAnalysisException notConfigured() {
        return new AiAnalysisException("ai api key is not configured");
    }
}
