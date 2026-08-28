package com.naeil.study.analysis.client;

import com.naeil.study.analysis.client.dto.AiChunkAnalysisRequest;
import com.naeil.study.analysis.client.dto.AiGeneralKnowledgeRequest;
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

    /**
     * 강의자료 없이 과목명과 시험 범위만으로 학습 주제를 만든다.
     *
     * <p>자료가 없는 사용자에게도 학습 순서를 줄 수 있어야 한다. 다만 특정 교수자의
     * 강의나 특정 교재를 아는 것처럼 굴면 안 된다 — 표준 교과 지식만 쓴다.
     *
     * <p>반환 형식은 자료 기반 경로와 같다. 이후 검증·저장·계획 생성이 같은 길을 지난다.
     * 출처 문서 참조만 비어 있다.
     */
    AiTopicAnalysisResult generateFromGeneralKnowledge(AiGeneralKnowledgeRequest request);
}
