package com.naeil.study.quiz.dto;

import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.topic.entity.Topic;
import java.util.List;
import java.util.UUID;

/**
 * Topic 하나의 문제 목록 응답. 문제는 출제 순서({@code order}) 오름차순이다.
 */
public record QuizListResponse(
        UUID topicId,
        String topicTitle,
        List<QuizResponse> quizzes
) {

    public static QuizListResponse of(Topic topic, List<Quiz> quizzes) {
        return new QuizListResponse(
                topic.getId(),
                topic.getTitle(),
                quizzes.stream().map(QuizResponse::from).toList()
        );
    }
}
