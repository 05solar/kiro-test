package com.naeil.study.quiz.dto;

import com.naeil.study.quiz.entity.QuizResult;
import com.naeil.study.quiz.service.QuizAnswerService.TopicQuizResults;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Topic 의 채점 집계 응답.
 *
 * <p>{@code scorePercentage} 는 전체 문제 수 기준의 정수 백분율이다.
 * 아직 다 풀지 않았으면({@code completed = false}) 현재까지의 값이며,
 * 성취도 판단은 다음 단계에서 {@code completed = true} 인 경우에만 한다.
 *
 * @param results 답한 문제들의 결과 (문제 순서). 정답 인덱스와 해설은 담지 않는다 —
 *                채점 응답에서 이미 공개했고, 안 푼 문제의 정답이 새 나갈 경로를 만들지 않는다
 */
public record QuizResultsResponse(
        UUID topicId,
        int totalQuestions,
        int answeredQuestions,
        int correctAnswers,
        int scorePercentage,
        boolean completed,
        List<AnsweredQuizResponse> results
) {

    public record AnsweredQuizResponse(
            UUID quizId,
            int selectedIndex,
            boolean correct,
            LocalDateTime answeredAt
    ) {

        static AnsweredQuizResponse from(QuizResult result) {
            return new AnsweredQuizResponse(
                    result.getQuiz().getId(),
                    result.getSelectedIndex(),
                    result.isCorrect(),
                    result.getAnsweredAt());
        }
    }

    public static QuizResultsResponse from(TopicQuizResults results) {
        return new QuizResultsResponse(
                results.topic().getId(),
                results.totalQuestions(),
                results.answeredQuestions(),
                results.correctAnswers(),
                results.scorePercentage(),
                results.completed(),
                results.results().stream().map(AnsweredQuizResponse::from).toList()
        );
    }
}
