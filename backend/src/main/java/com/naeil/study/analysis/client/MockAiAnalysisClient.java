package com.naeil.study.analysis.client;

import com.naeil.study.analysis.client.dto.AiChunkAnalysisRequest;
import com.naeil.study.analysis.client.dto.AiDocumentReference;
import com.naeil.study.analysis.client.dto.AiGeneralKnowledgeRequest;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicCandidate;
import com.naeil.study.analysis.client.dto.AiTopicCandidates;
import com.naeil.study.analysis.client.dto.AiTopicMergeRequest;
import com.naeil.study.analysis.client.dto.AiTopicResult;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 를 부르지 않고 분석 결과를 만들어 내는 구현.
 *
 * <p>화면과 흐름을 손으로 확인할 때마다 실제 AI 를 부르면 매번 과금된다.
 * 분석은 자료 조각 수만큼 호출하므로 특히 비싸다.
 * {@code ai.mode=mock} 이면 이 구현이 붙는다.
 *
 * <p><b>검증기가 통과시킬 수 있는 형태로 만든다.</b> 제목·요약·핵심 개념이 비지 않고,
 * 학습시간은 5~120분, 출처 참조는 요청에 실제로 있던 값만 쓴다. 검증을 우회하지 않는다.
 *
 * <p>일반 지식 경로는 시험 범위를 쉼표·줄바꿈으로 잘라 주제를 만든다.
 * "정렬, 검색, 트리" 를 넣으면 주제 세 개가 나온다. 실제 AI 처럼 교과 구조를 이해하지는
 * 못하지만, <b>범위 입력이 주제 목록으로 이어지는 경로</b>는 그대로 확인할 수 있다.
 */
public class MockAiAnalysisClient implements AiAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(MockAiAnalysisClient.class);

    /** 주제마다 돌려 쓰는 중요도. 압축 정책이 실제로 갈리는지 보려면 섞여 있어야 한다. */
    private static final String[] IMPORTANCE = {"VERY_HIGH", "HIGH", "MEDIUM", "HIGH", "LOW"};
    private static final int[] MINUTES = {40, 35, 30, 45, 25};

    @Override
    public AiTopicCandidates analyzeChunk(AiChunkAnalysisRequest request) {
        // 조각 하나당 후보 하나. 통합 단계에서 합쳐진다.
        return new AiTopicCandidates(List.of(new AiTopicCandidate(
                "%s 조각 %d".formatted(request.fileName(), request.chunkIndex() + 1),
                "목 데이터 요약입니다. 실제 자료 분석 결과가 아닙니다.",
                List.of("개념 A", "개념 B"))));
    }

    @Override
    public AiTopicAnalysisResult mergeTopics(AiTopicMergeRequest request) {
        // 조각 후보 수와 무관하게 고정된 개수를 낸다. 통합의 목적은 정리이지 개수 보존이 아니다.
        List<String> references = request.documents().stream()
                .map(AiDocumentReference::reference)
                .toList();
        int count = Math.min(request.maxTopics(), 4);

        List<AiTopicResult> topics = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            topics.add(topic(
                    "[목] %s 주제 %d".formatted(request.subject(), i + 1),
                    i,
                    // 요청에 있던 참조만 쓴다. 지어내면 검증기가 버린다.
                    references.isEmpty() ? List.of() : List.of(references.get(i % references.size()))));
        }
        log.info("mock analysis merged: subject={}, topics={} (AI 를 호출하지 않았다)",
                request.subject(), topics.size());
        return new AiTopicAnalysisResult(topics);
    }

    @Override
    public AiTopicAnalysisResult generateFromGeneralKnowledge(AiGeneralKnowledgeRequest request) {
        List<String> parts = splitScope(request.examScope());
        int count = Math.min(request.maxTopics(), Math.max(parts.size(), 1));

        List<AiTopicResult> topics = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = i < parts.size() ? parts.get(i) : "%s 기초".formatted(request.subject());
            // 출처 문서가 없다. 빈 목록이 맞다 — 지어내면 검증기가 버린다.
            topics.add(topic("[목·일반지식] " + name, i, List.of()));
        }
        log.info("mock general knowledge: subject={}, scope parts={}, topics={} (AI 를 호출하지 않았다)",
                request.subject(), parts.size(), topics.size());
        return new AiTopicAnalysisResult(topics);
    }

    /**
     * 시험 범위를 주제 후보로 자른다.
     *
     * <p>쉼표·줄바꿈·가운뎃점으로 나눈다. "정렬, 검색, 트리" 처럼 적으면 주제가 셋이 된다.
     * 실제 AI 는 교과 구조를 이해해 나누지만, 목에서는 입력이 결과로 이어지는 것만 보이면 된다.
     */
    private List<String> splitScope(String examScope) {
        if (examScope == null || examScope.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(examScope.split("[,\n·]"))
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private AiTopicResult topic(String title, int index, List<String> sourceDocuments) {
        return new AiTopicResult(
                title,
                "목 데이터 요약입니다. 실제 분석 결과가 아닙니다.",
                List.of("핵심 개념 1", "핵심 개념 2"),
                IMPORTANCE[index % IMPORTANCE.length],
                MINUTES[index % MINUTES.length],
                false, false, false, false,
                sourceDocuments);
    }
}
