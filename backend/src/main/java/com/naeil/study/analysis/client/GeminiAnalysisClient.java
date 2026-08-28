package com.naeil.study.analysis.client;

import com.naeil.study.analysis.client.dto.AiChunkAnalysisRequest;
import com.naeil.study.analysis.client.dto.AiGeneralKnowledgeRequest;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicCandidates;
import com.naeil.study.analysis.client.dto.AiTopicMergeRequest;
import com.naeil.study.analysis.exception.AiAnalysisException;
import com.naeil.study.analysis.prompt.AnalysisPrompts;
import com.naeil.study.common.ai.GeminiTextClient;

/**
 * Gemini 기반 분석 클라이언트.
 *
 * <p>프롬프트는 Claude 구현과 완전히 같은 것을 쓴다({@link AnalysisPrompts}). 공급자가 바뀌어도
 * 분석 규칙이 달라질 이유가 없다. 다른 점은 구조화 출력 방식뿐이다 — Gemini REST 에는
 * 응답 타입을 클래스로 넘기는 기능이 없으므로, 기대하는 JSON 필드 구조를 사용자 메시지에
 * 명시하고 응답을 DTO 로 매핑한다. 값 검증은 기존 Validator 가 그대로 담당한다.
 */
public class GeminiAnalysisClient implements AiAnalysisClient {

    private static final String CHUNK_OUTPUT_FORMAT = """

            # OUTPUT FORMAT (JSON only)
            {"topics": [{"title": "주제 이름(최대 200자)", "summary": "핵심 요약", "keyPoints": ["핵심 개념"]}]}
            학습 내용이 없는 조각이면 {"topics": []} 을 돌려준다.
            """;

    private static final String MERGE_OUTPUT_FORMAT = """

            # OUTPUT FORMAT (JSON only)
            {"topics": [{
              "title": "주제 이름(최대 200자)",
              "summary": "핵심 요약",
              "keyPoints": ["핵심 개념 (최소 1개)"],
              "importance": "VERY_HIGH | HIGH | MEDIUM | LOW",
              "estimatedStudyMinutes": 30,
              "professorEmphasisMatched": false,
              "pastExamMatched": false,
              "weakAreaMatched": false,
              "mustStudyMatched": false,
              "sourceDocuments": ["DOC_1"]
            }]}
            """;

    private final GeminiTextClient client;

    public GeminiAnalysisClient(GeminiTextClient client) {
        this.client = client;
    }

    @Override
    public AiTopicCandidates analyzeChunk(AiChunkAnalysisRequest request) {
        try {
            return client.generate(
                    AnalysisPrompts.chunkAnalysisSystemPrompt(),
                    AnalysisPrompts.chunkAnalysisUserMessage(request) + CHUNK_OUTPUT_FORMAT,
                    AiTopicCandidates.class,
                    "chunk analysis (%s #%d)".formatted(request.documentReference(), request.chunkIndex()));
        } catch (AiAnalysisException e) {
            throw e;
        } catch (RuntimeException e) {
            // 원인 메시지를 reason 에 함께 남긴다. 어떤 실패였는지 로그만으로 알 수 있어야 한다
            throw new AiAnalysisException("ai call failed: chunk analysis - " + e.getMessage(), e);
        }
    }

    @Override
    public AiTopicAnalysisResult mergeTopics(AiTopicMergeRequest request) {
        try {
            return client.generate(
                    AnalysisPrompts.mergeSystemPrompt(),
                    AnalysisPrompts.mergeUserMessage(request) + MERGE_OUTPUT_FORMAT,
                    AiTopicAnalysisResult.class,
                    "topic merge");
        } catch (AiAnalysisException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AiAnalysisException("ai call failed: topic merge - " + e.getMessage(), e);
        }
    }

    @Override
    public AiTopicAnalysisResult generateFromGeneralKnowledge(AiGeneralKnowledgeRequest request) {
        try {
            // 반환 형식은 병합 경로와 같다. 이후 검증·저장이 같은 길을 지난다.
            return client.generate(
                    AnalysisPrompts.generalKnowledgeSystemPrompt(),
                    AnalysisPrompts.generalKnowledgeUserMessage(request) + MERGE_OUTPUT_FORMAT,
                    AiTopicAnalysisResult.class,
                    "general knowledge topics");
        } catch (AiAnalysisException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AiAnalysisException(
                    "ai call failed: general knowledge topics - " + e.getMessage(), e);
        }
    }
}
