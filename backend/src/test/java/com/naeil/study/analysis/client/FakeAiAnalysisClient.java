package com.naeil.study.analysis.client;

import com.naeil.study.analysis.client.dto.AiChunkAnalysisRequest;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicCandidate;
import com.naeil.study.analysis.client.dto.AiTopicCandidates;
import com.naeil.study.analysis.client.dto.AiTopicMergeRequest;
import com.naeil.study.analysis.client.dto.AiTopicResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 테스트용 가짜 AI 클라이언트.
 *
 * <p><b>테스트에서는 실제 AI API를 부르지 않는다.</b> 과금이 발생하고, 응답이 매번 달라
 * 테스트가 흔들리며, 네트워크가 없으면 실패한다.
 *
 * <p>기본은 고정된 정상 응답을 돌려준다. 실패 상황은 {@link #failChunkAnalysis} /
 * {@link #failMerge} 로 바꾼다.
 */
public class FakeAiAnalysisClient implements AiAnalysisClient {

    /** 실제로 받은 요청. 프롬프트에 무엇이 들어갔는지 검증할 때 쓴다. */
    private final List<AiChunkAnalysisRequest> chunkRequests = new ArrayList<>();
    private final List<AiTopicMergeRequest> mergeRequests = new ArrayList<>();
    private final AtomicInteger mergeCallCount = new AtomicInteger();

    private Supplier<AiTopicCandidates> chunkResponse = FakeAiAnalysisClient::defaultCandidates;
    private Supplier<AiTopicAnalysisResult> mergeResponse = FakeAiAnalysisClient::defaultResult;

    /** 일반 지식 경로로 들어온 요청. 시험 범위가 실제로 전달됐는지 확인할 때 쓴다. */
    private final java.util.List<com.naeil.study.analysis.client.dto.AiGeneralKnowledgeRequest>
            generalKnowledgeRequests = new ArrayList<>();

    @Override
    public AiTopicCandidates analyzeChunk(AiChunkAnalysisRequest request) {
        chunkRequests.add(request);
        return chunkResponse.get();
    }

    @Override
    public AiTopicAnalysisResult generateFromGeneralKnowledge(
            com.naeil.study.analysis.client.dto.AiGeneralKnowledgeRequest request) {
        generalKnowledgeRequests.add(request);
        return mergeResponse.get();
    }

    /** 일반 지식 경로로 들어온 요청. 시험 범위가 실제로 전달됐는지 확인할 때 쓴다. */
    public java.util.List<com.naeil.study.analysis.client.dto.AiGeneralKnowledgeRequest>
            generalKnowledgeRequests() {
        return generalKnowledgeRequests;
    }

    @Override
    public AiTopicAnalysisResult mergeTopics(AiTopicMergeRequest request) {
        mergeRequests.add(request);
        mergeCallCount.incrementAndGet();
        return mergeResponse.get();
    }

    public static AiTopicCandidates defaultCandidates() {
        return new AiTopicCandidates(List.of(new AiTopicCandidate(
                "CPU 스케줄링",
                "준비 큐에서 CPU를 할당할 프로세스를 결정하는 과정이다.",
                List.of("FCFS", "Round Robin"))));
    }

    public static AiTopicAnalysisResult defaultResult() {
        return new AiTopicAnalysisResult(List.of(new AiTopicResult(
                "CPU 스케줄링",
                "테스트 요약",
                List.of("FCFS", "Round Robin"),
                "HIGH",
                30,
                false,
                true,
                false,
                false,
                List.of("DOC_1"))));
    }

    /** 테스트 간 기록이 섞이지 않도록 초기화한다. 이 빈은 컨텍스트에서 공유된다. */
    public void reset() {
        chunkRequests.clear();
        mergeRequests.clear();
        generalKnowledgeRequests.clear();
        mergeCallCount.set(0);
        chunkResponse = FakeAiAnalysisClient::defaultCandidates;
        mergeResponse = FakeAiAnalysisClient::defaultResult;
    }

    public void respondChunkWith(Supplier<AiTopicCandidates> response) {
        this.chunkResponse = response;
    }

    public void respondMergeWith(Supplier<AiTopicAnalysisResult> response) {
        this.mergeResponse = response;
    }

    public void failChunkAnalysis(RuntimeException error) {
        this.chunkResponse = () -> {
            throw error;
        };
    }

    public void failMerge(RuntimeException error) {
        this.mergeResponse = () -> {
            throw error;
        };
    }

    public List<AiChunkAnalysisRequest> chunkRequests() {
        return chunkRequests;
    }

    public List<AiTopicMergeRequest> mergeRequests() {
        return mergeRequests;
    }

    public int mergeCallCount() {
        return mergeCallCount.get();
    }
}
