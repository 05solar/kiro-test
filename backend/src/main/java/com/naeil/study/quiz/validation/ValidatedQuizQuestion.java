package com.naeil.study.quiz.validation;

import com.naeil.study.quiz.entity.QuizDifficulty;
import java.util.List;

/**
 * 검증을 마친 문제 하나. 저장 직전의 값이다.
 *
 * @param order 1부터 시작하는 출제 순서
 */
public record ValidatedQuizQuestion(
        String question,
        List<String> options,
        int correctIndex,
        String explanation,
        QuizDifficulty difficulty,
        int order
) {
}
