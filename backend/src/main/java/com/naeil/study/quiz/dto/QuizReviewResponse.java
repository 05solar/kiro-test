package com.naeil.study.quiz.dto;

import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.quiz.entity.QuizDifficulty;
import com.naeil.study.quiz.entity.QuizResult;
import com.naeil.study.quiz.service.QuizAnswerService.QuizReviewItem;
import com.naeil.study.quiz.service.QuizAnswerService.TopicQuizReview;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Topic 풀이 내역 응답.
 *
 * <p>{@link QuizResultsResponse} 와 다른 점은 문제 문장·보기·정답·해설을 함께 담는다는
 * 것이다. 그쪽은 점수 집계용이라 숫자만 있으면 되지만, 내역 화면은 "무엇을 틀렸는지"를
 * 보여 줘야 하므로 문제 자체가 필요하다.
 *
 * <p><b>정답과 해설은 답한 문제에만 담는다.</b> 안 푼 문제는 {@code correctIndex} 와
 * {@code explanation} 이 {@code null} 이다. 답안 제출 시 이미 공개한 것을 다시 보여 주는
 * 것은 괜찮지만, 이 경로로 안 푼 문제의 정답을 미리 볼 수 있어서는 안 된다.
 *
 * @param round 마지막 회차. "새로운 퀴즈"를 만들면 올라간다
 */
public record QuizReviewResponse(
        UUID topicId,
        String topicTitle,
        int round,
        int totalQuestions,
        int answeredQuestions,
        int wrongQuestions,
        List<QuizReviewItemResponse> items
) {

    /**
     * 문제 하나의 내역.
     *
     * @param selectedIndex 내가 고른 보기. 안 풀었으면 {@code null}
     * @param correctIndex  정답 보기. <b>답한 문제에만</b> 담는다
     * @param explanation   해설. <b>답한 문제에만</b> 담는다
     */
    public record QuizReviewItemResponse(
            UUID quizId,
            int quizOrder,
            String question,
            List<String> options,
            QuizDifficulty difficulty,
            boolean answered,
            Integer selectedIndex,
            boolean correct,
            Integer correctIndex,
            String explanation,
            LocalDateTime answeredAt
    ) {

        /** 정리 화면(review 도메인)도 이 변환을 쓴다. */
        public static QuizReviewItemResponse from(QuizReviewItem item) {
            Quiz quiz = item.quiz();
            QuizResult result = item.result();
            boolean answered = item.answered();

            return new QuizReviewItemResponse(
                    quiz.getId(),
                    quiz.getQuizOrder(),
                    quiz.getQuestion(),
                    quiz.getOptions(),
                    quiz.getDifficulty(),
                    answered,
                    answered ? result.getSelectedIndex() : null,
                    answered && result.isCorrect(),
                    // 안 푼 문제의 정답은 내보내지 않는다. 여기가 정답 미리보기 통로가 되면 안 된다.
                    answered ? quiz.getCorrectIndex() : null,
                    answered ? quiz.getExplanation() : null,
                    answered ? result.getAnsweredAt() : null);
        }
    }

    public static QuizReviewResponse from(TopicQuizReview review) {
        return new QuizReviewResponse(
                review.topic().getId(),
                review.topic().getTitle(),
                review.round(),
                review.items().size(),
                review.answeredQuestions(),
                review.wrongQuestions(),
                review.items().stream().map(QuizReviewItemResponse::from).toList()
        );
    }
}
