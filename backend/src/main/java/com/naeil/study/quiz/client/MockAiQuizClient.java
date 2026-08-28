package com.naeil.study.quiz.client;

import com.naeil.study.quiz.client.dto.AiQuizGenerationRequest;
import com.naeil.study.quiz.client.dto.AiQuizGenerationResult;
import com.naeil.study.quiz.client.dto.AiQuizQuestion;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 를 부르지 않고 문제를 만들어 내는 구현.
 *
 * <p>화면과 흐름을 손으로 확인할 때마다 실제 AI 를 부르면 매번 과금된다.
 * "새로운 퀴즈 만들기"처럼 여러 번 눌러 보게 되는 기능은 특히 그렇다.
 * {@code quiz.ai-mode=mock} 이면 이 구현이 붙는다.
 *
 * <p><b>생성 규칙이 실제와 같은 계약을 지킨다.</b> 보기 4개, 정답 인덱스 0~3,
 * 최소 문항 수 — 검증기가 통과시킬 수 있는 형태로 만든다. 검증기를 우회하지 않는다.
 *
 * <p><b>회차마다 다른 문제를 낸다.</b> 이전 문제 목록을 받아 그만큼 번호를 밀어
 * 중복 방지 흐름 자체를 목 모드에서도 확인할 수 있게 한다. 실제 AI 처럼 의미까지
 * 다르게 만들지는 못하지만, "이전 문제를 넘겨받아 다른 문제가 나온다"는 경로는 같다.
 */
public class MockAiQuizClient implements AiQuizClient {

    private static final Logger log = LoggerFactory.getLogger(MockAiQuizClient.class);

    /** 문제마다 돌려 쓰는 난이도. 실제 응답과 같은 분포를 흉내 낸다. */
    private static final String[] DIFFICULTIES = {"EASY", "MEDIUM", "MEDIUM", "MEDIUM", "HARD"};

    @Override
    public AiQuizGenerationResult generate(AiQuizGenerationRequest request) {
        int already = request.hasPrevious() ? request.previousQuestions().size() : 0;
        List<AiQuizQuestion> questions = new ArrayList<>(request.questionCount());

        for (int i = 0; i < request.questionCount(); i++) {
            // 이전 회차 문항 수만큼 번호를 밀어 같은 문장이 다시 나오지 않게 한다.
            int number = already + i + 1;
            String keyPoint = keyPointAt(request, i);

            questions.add(new AiQuizQuestion(
                    "[목 데이터 %d] %s — %s 에 대한 설명으로 옳은 것은?"
                            .formatted(number, request.topicTitle(), keyPoint),
                    List.of(
                            "%s 에 대한 올바른 설명 (문항 %d)".formatted(keyPoint, number),
                            "틀린 설명 A (문항 %d)".formatted(number),
                            "틀린 설명 B (문항 %d)".formatted(number),
                            "틀린 설명 C (문항 %d)".formatted(number)),
                    // 정답 위치를 고르게 흩는다. 항상 0이면 화면에서 정답 표시를 잘못 짜도 드러나지 않는다.
                    number % 4,
                    "목 데이터입니다. 실제 해설이 아닙니다. (%s, 문항 %d)".formatted(keyPoint, number),
                    DIFFICULTIES[i % DIFFICULTIES.length]));
        }

        log.info("mock quiz generated: topic={}, count={}, previous={} (AI 를 호출하지 않았다)",
                request.topicTitle(), questions.size(), already);
        return new AiQuizGenerationResult(questions);
    }

    /** 정답 보기에 넣을 핵심 개념. 없으면 주제 제목을 쓴다. */
    private String keyPointAt(AiQuizGenerationRequest request, int index) {
        List<String> keyPoints = request.keyPoints();
        if (keyPoints == null || keyPoints.isEmpty()) {
            return request.topicTitle();
        }
        return keyPoints.get(index % keyPoints.size());
    }
}
