package com.naeil.study.review.dto;

import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.entity.StudyStepStatus;
import com.naeil.study.curriculum.entity.StudyStepType;
import com.naeil.study.quiz.dto.QuizReviewResponse.QuizReviewItemResponse;
import com.naeil.study.review.service.SessionReviewService.SessionReview;
import com.naeil.study.review.service.SessionReviewService.StepReview;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 세션 전체 정리 응답.
 *
 * <p>화면 세 가지(스텝별 요약 / 푼 문제 전체 / 틀린 문제만)가 모두 이 응답 하나로
 * 그려진다. 틀린 문제만 보는 화면도 따로 부르지 않고 {@code correct = false} 인 것만
 * 골라 쓴다 — 같은 자료를 세 번 받아 갈 이유가 없다.
 *
 * @param completed 모든 스텝을 마쳤는가. 화면은 이 값으로 정리 기능을 열지 말지 정한다
 */
public record SessionReviewResponse(
        String sessionCode,
        String subject,
        String examScope,
        LocalDateTime examAt,
        boolean completed,
        int totalSteps,
        int completedSteps,
        int totalQuestions,
        int answeredQuestions,
        int correctAnswers,
        int wrongAnswers,
        int scorePercentage,
        List<StepReviewResponse> steps
) {

    /**
     * 스텝 하나의 정리.
     *
     * @param topicId      Topic 이 없는 스텝(휴식 등)이면 {@code null}
     * @param summary      분석 단계에서 만들어 둔 요약. 정리 화면에서 새로 만들지 않는다
     * @param round        마지막 퀴즈 회차. 퀴즈를 만든 적 없으면 0
     * @param quizzes      마지막 회차의 문제와 답안. 퀴즈가 없으면 빈 목록
     */
    public record StepReviewResponse(
            UUID stepId,
            int stepOrder,
            String title,
            StudyStepType type,
            StudyStepStatus status,
            int allocatedMinutes,
            Integer actualStudyMinutes,
            UUID topicId,
            String topicTitle,
            String summary,
            List<String> keyPoints,
            TopicImportance importance,
            int round,
            int totalQuestions,
            int answeredQuestions,
            int wrongQuestions,
            List<QuizReviewItemResponse> quizzes
    ) {

        static StepReviewResponse from(StepReview review) {
            StudyStep step = review.step();
            Topic topic = review.topic();

            return new StepReviewResponse(
                    step.getId(),
                    step.getStepOrder(),
                    step.getTitle(),
                    step.getType(),
                    step.getStatus(),
                    step.getAllocatedMinutes(),
                    step.getActualStudyMinutes(),
                    topic == null ? null : topic.getId(),
                    topic == null ? null : topic.getTitle(),
                    topic == null ? null : topic.getSummary(),
                    topic == null ? List.of() : topic.getKeyPoints(),
                    topic == null ? null : topic.getImportance(),
                    review.round(),
                    review.items().size(),
                    review.answeredQuestions(),
                    review.wrongQuestions(),
                    review.items().stream().map(QuizReviewItemResponse::from).toList());
        }
    }

    public static SessionReviewResponse from(SessionReview review) {
        StudySession session = review.session();

        return new SessionReviewResponse(
                session.getSessionCode(),
                session.getSubject(),
                session.getExamScope(),
                session.getExamAt(),
                review.completed(),
                review.totalSteps(),
                review.completedSteps(),
                review.totalQuestions(),
                review.answeredQuestions(),
                review.correctAnswers(),
                review.wrongAnswers(),
                review.scorePercentage(),
                review.steps().stream().map(StepReviewResponse::from).toList()
        );
    }
}
