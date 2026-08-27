package com.naeil.study.quiz.dto;

import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.quiz.entity.QuizDifficulty;
import java.util.List;
import java.util.UUID;

/**
 * 문제 하나의 응답.
 *
 * <p><b>{@code correctIndex} 와 {@code explanation} 이 없다. 실수가 아니다.</b>
 * 문제를 내려줄 때 정답 정보가 함께 나가면 클라이언트에서 정답을 조작하거나 미리 볼 수 있다.
 * 두 값은 답안 제출 응답({@link QuizAnswerResponse})에서만 공개한다.
 */
public record QuizResponse(
        UUID id,
        int order,
        String question,
        List<String> options,
        QuizDifficulty difficulty
) {

    public static QuizResponse from(Quiz quiz) {
        return new QuizResponse(
                quiz.getId(),
                quiz.getQuizOrder(),
                quiz.getQuestion(),
                quiz.getOptions(),
                quiz.getDifficulty()
        );
    }
}
