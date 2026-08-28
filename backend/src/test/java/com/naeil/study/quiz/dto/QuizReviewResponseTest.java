package com.naeil.study.quiz.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.quiz.dto.QuizReviewResponse.QuizReviewItemResponse;
import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.quiz.entity.QuizDifficulty;
import com.naeil.study.quiz.entity.QuizResult;
import com.naeil.study.quiz.service.QuizAnswerService.QuizReviewItem;
import com.naeil.study.quiz.service.QuizAnswerService.TopicQuizReview;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 내역 응답이 안 푼 문제의 정답을 흘리지 않는지 본다.
 *
 * <p>이 화면은 정답과 해설을 담는 유일한 조회 API 다. 답한 문제만 담는다는 규칙이
 * 무너지면, 문제를 풀기 전에 내역을 먼저 열어 정답을 보는 길이 열린다.
 */
@DisplayName("QuizReviewResponse - 안 푼 문제의 정답 감추기")
class QuizReviewResponseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 21, 0, 0);

    private StudySession session;
    private Topic topic;

    @BeforeEach
    void setUp() {
        session = StudySession.create("7K2M9QXF", NOW.minusHours(5), 30L);
        topic = Topic.create(session, "CPU 스케줄링", "요약", List.of("Round Robin"),
                TopicImportance.HIGH, 40, false, false, false, false, List.of(), 1, NOW.minusHours(1));
    }

    private Quiz quiz(int order, int correctIndex) {
        return Quiz.create(topic, 1, order, "문제 " + order, List.of("A", "B", "C", "D"),
                correctIndex, "해설 " + order, QuizDifficulty.MEDIUM, List.of(), NOW.minusMinutes(30));
    }

    @Test
    @DisplayName("안 푼 문제는 정답과 해설을 담지 않는다")
    void hidesAnswerOfUnansweredQuiz() {
        QuizReviewItemResponse item =
                QuizReviewItemResponse.from(new QuizReviewItem(quiz(1, 2), null));

        assertThat(item.answered()).isFalse();
        assertThat(item.correctIndex()).isNull();
        assertThat(item.explanation()).isNull();
        assertThat(item.selectedIndex()).isNull();
        assertThat(item.answeredAt()).isNull();
        // 문제와 보기는 그대로 둔다. 무엇을 안 풀었는지는 보여야 한다.
        assertThat(item.question()).isEqualTo("문제 1");
        assertThat(item.options()).hasSize(4);
    }

    @Test
    @DisplayName("푼 문제는 정답과 해설을 담는다")
    void revealsAnswerOfAnsweredQuiz() {
        Quiz quiz = quiz(1, 2);
        QuizReviewItemResponse item = QuizReviewItemResponse.from(
                new QuizReviewItem(quiz, QuizResult.create(session, quiz, 3, false, NOW)));

        assertThat(item.answered()).isTrue();
        assertThat(item.correct()).isFalse();
        assertThat(item.selectedIndex()).isEqualTo(3);
        assertThat(item.correctIndex()).isEqualTo(2);
        assertThat(item.explanation()).isEqualTo("해설 1");
        assertThat(item.answeredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("집계 수치를 함께 담는다")
    void countsAnsweredAndWrong() {
        Quiz first = quiz(1, 0);
        Quiz second = quiz(2, 0);
        Quiz third = quiz(3, 0);

        QuizReviewResponse response = QuizReviewResponse.from(new TopicQuizReview(topic, 2, List.of(
                new QuizReviewItem(first, QuizResult.create(session, first, 0, true, NOW)),
                new QuizReviewItem(second, QuizResult.create(session, second, 1, false, NOW)),
                new QuizReviewItem(third, null))));

        assertThat(response.round()).isEqualTo(2);
        assertThat(response.totalQuestions()).isEqualTo(3);
        assertThat(response.answeredQuestions()).isEqualTo(2);
        assertThat(response.wrongQuestions()).isEqualTo(1);
        assertThat(response.topicTitle()).isEqualTo("CPU 스케줄링");
    }
}
