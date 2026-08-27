package com.naeil.study.quiz.client;

import com.naeil.study.quiz.client.dto.AiQuizGenerationRequest;
import com.naeil.study.quiz.client.dto.AiQuizGenerationResult;
import com.naeil.study.quiz.client.dto.AiQuizQuestion;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 테스트용 가짜 AI 퀴즈 클라이언트.
 *
 * <p><b>테스트에서는 실제 AI API를 부르지 않는다.</b> 과금이 발생하고, 응답이 매번 달라
 * 테스트가 흔들리며, 네트워크가 없으면 실패한다.
 *
 * <p>기본은 고정된 정상 5문제를 돌려준다. 실패 상황은 {@link #failWith} 로 바꾼다.
 */
public class FakeAiQuizClient implements AiQuizClient {

    /** 실제로 받은 요청. 프롬프트에 무엇이 들어갔는지 검증할 때 쓴다. */
    private final List<AiQuizGenerationRequest> requests = new ArrayList<>();

    private Supplier<AiQuizGenerationResult> response = FakeAiQuizClient::defaultResult;

    @Override
    public AiQuizGenerationResult generate(AiQuizGenerationRequest request) {
        requests.add(request);
        return response.get();
    }

    /** 정답 인덱스가 0~3에 고르게 퍼진 정상 5문제. */
    public static AiQuizGenerationResult defaultResult() {
        return new AiQuizGenerationResult(List.of(
                question("Round Robin 스케줄링의 특징으로 가장 적절한 것은?", 0, "EASY"),
                question("FCFS 스케줄링에 대한 설명으로 옳은 것은?", 1, "MEDIUM"),
                question("SJF 스케줄링의 단점으로 가장 적절한 것은?", 2, "MEDIUM"),
                question("Time Quantum 의 역할로 옳은 것은?", 3, "MEDIUM"),
                question("선점형 스케줄링에 해당하는 것은?", 0, "HARD")));
    }

    public static AiQuizQuestion question(String text, int correctIndex, String difficulty) {
        return new AiQuizQuestion(
                text,
                List.of("보기 A", "보기 B", "보기 C", "보기 D"),
                correctIndex,
                "강의자료에 따르면 그렇다.",
                difficulty);
    }

    /** 테스트 간 기록이 섞이지 않도록 초기화한다. 이 빈은 컨텍스트에서 공유된다. */
    public void reset() {
        requests.clear();
        response = FakeAiQuizClient::defaultResult;
    }

    public void respondWith(Supplier<AiQuizGenerationResult> response) {
        this.response = response;
    }

    public void failWith(RuntimeException error) {
        this.response = () -> {
            throw error;
        };
    }

    public List<AiQuizGenerationRequest> requests() {
        return requests;
    }

    public int callCount() {
        return requests.size();
    }
}
