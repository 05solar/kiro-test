package com.naeil.study.analysis.client;

import com.naeil.study.analysis.client.dto.AiChunkAnalysisRequest;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicCandidates;
import com.naeil.study.analysis.client.dto.AiTopicMergeRequest;

/**
 * AI 분석 호출 추상화.
 *
 * <p>도메인 서비스는 이 인터페이스만 안다. 어느 LLM을 쓰는지, SDK가 무엇인지 모른다.
 * 공급자를 바꿔도 분석 로직은 그대로다.
 *
 * <p>분석은 두 단계다.
 * <pre>
 * 1차  조각별로 주제 후보만 뽑는다        analyzeChunk
 * 2차  후보를 합쳐 최종 Topic을 만든다     mergeTopics
 * </pre>
 * 조각 하나만 보고는 전체에서의 우선순위를 알 수 없어서 중요도와 학습시간은 2차에서 정한다.
 *
 * <p>구현체는 실패를 {@link com.naeil.study.analysis.exception.AiAnalysisException}으로 감싼다.
 * SDK 예외를 그대로 올리지 않는다.
 */
public interface AiAnalysisClient {

    /** 강의자료 조각 하나에서 주제 후보를 뽑는다. */
    AiTopicCandidates analyzeChunk(AiChunkAnalysisRequest request);

    /** 주제 후보들을 하나의 Topic 목록으로 합친다. */
    AiTopicAnalysisResult mergeTopics(AiTopicMergeRequest request);
}
