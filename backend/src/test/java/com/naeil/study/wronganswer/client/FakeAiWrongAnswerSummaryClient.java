package com.naeil.study.wronganswer.client;

import com.naeil.study.wronganswer.client.dto.AiTopicReview;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerSummaryRequest;
import com.naeil.study.wronganswer.client.dto.AiWrongAnswerSummaryResult;
import java.util.List;
import java.util.function.Function;

/**
 * 테스트용 가짜 AI 오답 요약 클라이언트.
 *
 * <p><b>테스트에서는 실제 AI API를 부르지 않는다.</b>
 *
 * <p>기본은 요청에 들어온 Topic 참조값을 그대로 사용해 정상 응답을 만든다.
 * 그래서 어떤 Topic 조합이 와도 참조 검증을 통과한다.
 */
public class FakeAiWrongAnswerSummaryClient implements AiWrongAnswerSummaryClient {

    /** 실제로 받은 요청. 오답만 전달되는지 검증할 때 쓴다. */
    private final java.util.List<AiWrongAnswerSummaryRequest> requests = new java.util.ArrayList<>();

    private Function<AiWrongAnswerSummaryRequest, AiWrongAnswerSummaryResult> response =
            FakeAiWrongAnswerSummaryClient::defaultResult;

    @Override
    public AiWrongAnswerSummaryResult generate(AiWrongAnswerSummaryRequest request) {
        requests.add(request);
        return response.apply(request);
    }

    /** 요청의 Topic 참조를 그대로 되돌려주는 정상 응답. */
    public static AiWrongAnswerSummaryResult defaultResult(AiWrongAnswerSummaryRequest request) {
        List<AiTopicReview> reviews = request.topics().stream()
                .map(topic -> new AiTopicReview(
                        topic.topicReference(),
                        List.of("틀린 개념 1"),
                        topic.topicTitle() + " 핵심 복습 설명이다.",
                        List.of("반드시 기억할 포인트"),
                        "HIGH"))
                .toList();
        return new AiWrongAnswerSummaryResult("오답이 있었던 영역을 우선 복습하세요.", reviews);
    }

    /** 테스트 간 기록이 섞이지 않도록 초기화한다. 이 빈은 컨텍스트에서 공유된다. */
    public void reset() {
        requests.clear();
        response = FakeAiWrongAnswerSummaryClient::defaultResult;
    }

    public void respondWith(Function<AiWrongAnswerSummaryRequest, AiWrongAnswerSummaryResult> response) {
        this.response = response;
    }

    public void failWith(RuntimeException error) {
        this.response = request -> {
            throw error;
        };
    }

    public List<AiWrongAnswerSummaryRequest> requests() {
        return requests;
    }

    public int callCount() {
        return requests.size();
    }
}
