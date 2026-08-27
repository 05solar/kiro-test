package com.naeil.study.quiz.dto;

import com.naeil.study.quiz.service.QuizAnswerService.AnswerResult;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 답안 제출(채점) 응답.
 *
 * <p>채점이 끝난 뒤이므로 여기서는 정답과 해설을 공개한다.
 * 답안은 최초 1회만 저장되므로, 같은 문제에 다시 제출해도 이 응답의 값은 바뀌지 않는다.
 *
 * @param correct 서버가 계산한 정답 여부
 */
public record QuizAnswerResponse(
        UUID quizId,
        int selectedIndex,
        int correctIndex,
        boolean correct,
        String explanation,
        LocalDateTime answeredAt
) {

    public static QuizAnswerResponse from(AnswerResult result) {
        return new QuizAnswerResponse(
                result.quiz().getId(),
                result.result().getSelectedIndex(),
                result.quiz().getCorrectIndex(),
                result.result().isCorrect(),
                result.quiz().getExplanation(),
                result.result().getAnsweredAt()
        );
    }
}
