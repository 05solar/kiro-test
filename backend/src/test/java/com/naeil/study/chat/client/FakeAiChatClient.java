package com.naeil.study.chat.client;

import com.naeil.study.chat.client.dto.AiChatAnswer;
import com.naeil.study.chat.client.dto.AiChatRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 테스트용 가짜 AI 챗봇 클라이언트.
 *
 * <p><b>테스트에서는 실제 AI API를 부르지 않는다.</b> 과금이 발생하고, 응답이 매번 달라
 * 테스트가 흔들리며, 네트워크가 없으면 실패한다.
 *
 * <p>받은 요청을 기록한다. 프롬프트에 무엇이 들어갔는지 — 특히 <b>무엇이 들어가지 않았는지</b>
 * — 를 검증하는 것이 이 챗봇 테스트의 핵심이다.
 */
public class FakeAiChatClient implements AiChatClient {

    private final List<AiChatRequest> requests = new ArrayList<>();

    private Function<AiChatRequest, AiChatAnswer> response =
            request -> new AiChatAnswer("가짜 답변입니다.", true);

    @Override
    public AiChatAnswer answer(AiChatRequest request) {
        requests.add(request);
        return response.apply(request);
    }

    /** 테스트 간 기록이 섞이지 않도록 초기화한다. 이 빈은 컨텍스트에서 공유된다. */
    public void reset() {
        requests.clear();
        response = request -> new AiChatAnswer("가짜 답변입니다.", true);
    }

    public void respondWith(Function<AiChatRequest, AiChatAnswer> response) {
        this.response = response;
    }

    public void failWith(RuntimeException error) {
        this.response = request -> {
            throw error;
        };
    }

    public List<AiChatRequest> requests() {
        return requests;
    }

    public AiChatRequest lastRequest() {
        return requests.get(requests.size() - 1);
    }

    public int callCount() {
        return requests.size();
    }
}
