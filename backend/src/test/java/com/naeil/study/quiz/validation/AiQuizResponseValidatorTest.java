package com.naeil.study.quiz.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.naeil.study.quiz.client.dto.AiQuizGenerationResult;
import com.naeil.study.quiz.client.dto.AiQuizQuestion;
import com.naeil.study.quiz.entity.QuizDifficulty;
import com.naeil.study.quiz.exception.QuizGenerationFailedException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AiQuizResponseValidator - AI 퀴즈 응답 검증")
class AiQuizResponseValidatorTest {

    private static final int MAX_QUESTIONS = 5;

    private final AiQuizResponseValidator validator = new AiQuizResponseValidator();

    private AiQuizQuestion question(String text, List<String> options, Integer correctIndex,
                                    String explanation, String difficulty) {
        return new AiQuizQuestion(text, options, correctIndex, explanation, difficulty);
    }

    private AiQuizQuestion normal(int index) {
        return question("문제 " + index, List.of("A" + index, "B" + index, "C" + index, "D" + index),
                index % 4, "해설 " + index, "MEDIUM");
    }

    private AiQuizGenerationResult resultOf(AiQuizQuestion... questions) {
        return new AiQuizGenerationResult(List.of(questions));
    }

    private AiQuizGenerationResult normalResult(int count) {
        List<AiQuizQuestion> questions = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            questions.add(normal(i));
        }
        return new AiQuizGenerationResult(questions);
    }

    @Test
    @DisplayName("정상 5문제를 검증하고 값을 다듬어 돌려준다")
    void validatesNormalQuestions() {
        List<ValidatedQuizQuestion> validated = validator.validate(normalResult(5), MAX_QUESTIONS);

        assertThat(validated).hasSize(5);
        assertThat(validated.get(0).order()).isEqualTo(1);
        assertThat(validated.get(4).order()).isEqualTo(5);
        assertThat(validated.get(0).difficulty()).isEqualTo(QuizDifficulty.MEDIUM);
        assertThat(validated).allSatisfy(question -> {
            assertThat(question.options()).hasSize(4);
            assertThat(question.correctIndex()).isBetween(0, 3);
        });
    }

    @Test
    @DisplayName("근거가 부족해 3문제만 만들어도 허용한다")
    void allowsMinimumThreeQuestions() {
        assertThat(validator.validate(normalResult(3), MAX_QUESTIONS)).hasSize(3);
    }

    @Test
    @DisplayName("문제가 없으면 실패한다")
    void failsWithoutQuestions() {
        assertThatThrownBy(() -> validator.validate(new AiQuizGenerationResult(List.of()), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
        assertThatThrownBy(() -> validator.validate(new AiQuizGenerationResult(null), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
        assertThatThrownBy(() -> validator.validate(null, MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
    }

    @Test
    @DisplayName("2문제만 반환하면 실패한다")
    void failsWithTooFewQuestions() {
        assertThatThrownBy(() -> validator.validate(normalResult(2), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
    }

    @Test
    @DisplayName("요청한 개수보다 많으면 실패한다")
    void failsWithTooManyQuestions() {
        assertThatThrownBy(() -> validator.validate(normalResult(6), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
    }

    @Test
    @DisplayName("보기가 3개면 실패한다")
    void failsWithThreeOptions() {
        AiQuizQuestion bad = question("문제", List.of("A", "B", "C"), 0, "해설", "EASY");

        assertThatThrownBy(() -> validator.validate(resultOf(normal(1), normal(2), bad), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
    }

    @Test
    @DisplayName("보기가 5개면 실패한다")
    void failsWithFiveOptions() {
        AiQuizQuestion bad = question("문제", List.of("A", "B", "C", "D", "E"), 0, "해설", "EASY");

        assertThatThrownBy(() -> validator.validate(resultOf(normal(1), normal(2), bad), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
    }

    @Test
    @DisplayName("정규화 후 같은 보기가 두 개면 실패한다")
    void failsWithDuplicatedOptions() {
        AiQuizQuestion bad = question("문제", List.of("Round Robin", " round robin ", "C", "D"),
                0, "해설", "EASY");

        assertThatThrownBy(() -> validator.validate(resultOf(normal(1), normal(2), bad), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
    }

    @Test
    @DisplayName("빈 보기가 있으면 실패한다")
    void failsWithBlankOption() {
        AiQuizQuestion bad = question("문제", List.of("A", " ", "C", "D"), 0, "해설", "EASY");

        assertThatThrownBy(() -> validator.validate(resultOf(normal(1), normal(2), bad), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
    }

    @Test
    @DisplayName("빈 문제 본문이면 실패한다")
    void failsWithBlankQuestion() {
        AiQuizQuestion bad = question("  ", List.of("A", "B", "C", "D"), 0, "해설", "EASY");

        assertThatThrownBy(() -> validator.validate(resultOf(normal(1), normal(2), bad), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
    }

    @Test
    @DisplayName("2000자를 넘는 문제 본문이면 실패한다")
    void failsWithTooLongQuestion() {
        AiQuizQuestion bad = question("문".repeat(2001), List.of("A", "B", "C", "D"), 0, "해설", "EASY");

        assertThatThrownBy(() -> validator.validate(resultOf(normal(1), normal(2), bad), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
    }

    @Test
    @DisplayName("correctIndex 가 -1 이면 실패한다")
    void failsWithNegativeCorrectIndex() {
        AiQuizQuestion bad = question("문제", List.of("A", "B", "C", "D"), -1, "해설", "EASY");

        assertThatThrownBy(() -> validator.validate(resultOf(normal(1), normal(2), bad), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
    }

    @Test
    @DisplayName("correctIndex 가 4 이면 실패한다")
    void failsWithCorrectIndexOutOfRange() {
        AiQuizQuestion bad = question("문제", List.of("A", "B", "C", "D"), 4, "해설", "EASY");

        assertThatThrownBy(() -> validator.validate(resultOf(normal(1), normal(2), bad), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
    }

    @Test
    @DisplayName("correctIndex 가 없으면 실패한다")
    void failsWithMissingCorrectIndex() {
        AiQuizQuestion bad = question("문제", List.of("A", "B", "C", "D"), null, "해설", "EASY");

        assertThatThrownBy(() -> validator.validate(resultOf(normal(1), normal(2), bad), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
    }

    @Test
    @DisplayName("빈 해설이면 실패한다")
    void failsWithBlankExplanation() {
        AiQuizQuestion bad = question("문제", List.of("A", "B", "C", "D"), 0, " ", "EASY");

        assertThatThrownBy(() -> validator.validate(resultOf(normal(1), normal(2), bad), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
    }

    @Test
    @DisplayName("지원하지 않는 난이도면 실패한다")
    void failsWithInvalidDifficulty() {
        AiQuizQuestion bad = question("문제", List.of("A", "B", "C", "D"), 0, "해설", "IMPOSSIBLE");

        assertThatThrownBy(() -> validator.validate(resultOf(normal(1), normal(2), bad), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
    }

    @Test
    @DisplayName("난이도 문자열의 대소문자와 공백은 허용한다")
    void normalizesDifficulty() {
        AiQuizQuestion lenient = question("문제", List.of("A", "B", "C", "D"), 0, "해설", " hard ");

        List<ValidatedQuizQuestion> validated =
                validator.validate(resultOf(normal(1), normal(2), lenient), MAX_QUESTIONS);

        assertThat(validated.get(2).difficulty()).isEqualTo(QuizDifficulty.HARD);
    }
}
