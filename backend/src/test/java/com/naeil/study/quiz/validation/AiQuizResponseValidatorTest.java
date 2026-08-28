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

    /*
     * 프롬프트 누출 정제.
     *
     * 시스템 프롬프트가 <lecture_context> 로 자료를 구분하는데, 모델이 그 이름을 문제 본문에
     * 그대로 쓰는 일이 실제로 있었다. 학생은 그런 태그를 본 적이 없다.
     */

    @Test
    @DisplayName("문제 본문의 태그와 출처 머리말을 지운다 — 실제로 나왔던 문장이다")
    void stripsPromptTagAndSourcePrefix() {
        AiQuizQuestion leaked = question(
                "<lecture_context>에 근거할 때, 요구 사항 도출 단계에서 소프트웨어 엔지니어가 "
                        + "시스템 이해관계자와 협력하여 파악해야 하는 사항으로 옳은 것은?",
                List.of("A", "B", "C", "D"), 0, "해설", "MEDIUM");

        List<ValidatedQuizQuestion> validated =
                validator.validate(resultOf(normal(1), normal(2), leaked), MAX_QUESTIONS);

        assertThat(validated.get(2).question()).isEqualTo(
                "요구 사항 도출 단계에서 소프트웨어 엔지니어가 시스템 이해관계자와 협력하여 "
                        + "파악해야 하는 사항으로 옳은 것은?");
    }

    @Test
    @DisplayName("태그 없이 머리말만 있어도 지운다")
    void stripsSourcePrefixWithoutTag() {
        AiQuizQuestion leaked = question(
                "제공된 강의자료에 따르면, 교착상태의 조건은?",
                List.of("A", "B", "C", "D"), 0,
                "위 자료에 근거하여 네 조건이 모두 필요하다.", "MEDIUM");

        List<ValidatedQuizQuestion> validated =
                validator.validate(resultOf(normal(1), normal(2), leaked), MAX_QUESTIONS);

        assertThat(validated.get(2).question()).isEqualTo("교착상태의 조건은?");
        assertThat(validated.get(2).explanation()).isEqualTo("네 조건이 모두 필요하다.");
    }

    @Test
    @DisplayName("과목명으로 시작하는 정상 문장은 건드리지 않는다")
    void keepsSubjectNameThatLooksLikePrefix() {
        // "자료구조에서..." 의 앞 두 글자가 "자료" 라고 지우면 안 된다.
        AiQuizQuestion normalText = question(
                "자료구조에서 스택의 특징으로 옳은 것은?",
                List.of("A", "B", "C", "D"), 0, "해설", "MEDIUM");

        List<ValidatedQuizQuestion> validated =
                validator.validate(resultOf(normal(1), normal(2), normalText), MAX_QUESTIONS);

        assertThat(validated.get(2).question()).isEqualTo("자료구조에서 스택의 특징으로 옳은 것은?");
    }

    @Test
    @DisplayName("문장 중간의 태그는 지우지 않고 '자료'로 바꾼다 — 지우면 문장이 깨진다")
    void replacesTagInsideSentence() {
        // DB 에 실제로 남아 있던 문장이다.
        AiQuizQuestion leaked = question(
                "소프트웨어 엔지니어가 요구 사항 도출 과정에서 협력하는 대상으로 "
                        + "<lecture_context>에서 언급된 것은 무엇인가?",
                List.of("A", "B", "C", "D"), 0, "해설", "MEDIUM");

        List<ValidatedQuizQuestion> validated =
                validator.validate(resultOf(normal(1), normal(2), leaked), MAX_QUESTIONS);

        assertThat(validated.get(2).question()).isEqualTo(
                "소프트웨어 엔지니어가 요구 사항 도출 과정에서 협력하는 대상으로 "
                        + "자료에서 언급된 것은 무엇인가?");
    }

    @Test
    @DisplayName("'…를 바탕으로 할 때,' 처럼 한 마디 더 붙은 머리말도 지운다")
    void stripsPrefixWithTrailingClause() {
        // 이것도 DB 에 실제로 남아 있던 문장이다.
        AiQuizQuestion leaked = question(
                "<lecture_context>를 바탕으로 할 때, 소프트웨어 엔지니어가 다양한 시스템 "
                        + "이해관계자와 협력하는 주된 이유가 아닌 것은?",
                List.of("A", "B", "C", "D"), 0, "해설", "MEDIUM");

        List<ValidatedQuizQuestion> validated =
                validator.validate(resultOf(normal(1), normal(2), leaked), MAX_QUESTIONS);

        assertThat(validated.get(2).question()).isEqualTo(
                "소프트웨어 엔지니어가 다양한 시스템 이해관계자와 협력하는 주된 이유가 아닌 것은?");
    }

    @Test
    @DisplayName("보기에 섞인 태그도 '자료'로 바꾼다")
    void replacesTagInOptions() {
        AiQuizQuestion leaked = question("문제",
                List.of("<lecture_context>에서 설명한 방식", "B", "C", "D"), 0, "해설", "MEDIUM");

        List<ValidatedQuizQuestion> validated =
                validator.validate(resultOf(normal(1), normal(2), leaked), MAX_QUESTIONS);

        assertThat(validated.get(2).options().get(0)).isEqualTo("자료에서 설명한 방식");
    }

    @Test
    @DisplayName("머리말만 있고 묻는 내용이 없으면 실패한다 — 고쳐 쓰지 않는다")
    void failsWhenNothingRemains() {
        AiQuizQuestion empty = question("<lecture_context>에 근거할 때,",
                List.of("A", "B", "C", "D"), 0, "해설", "MEDIUM");

        assertThatThrownBy(() -> validator.validate(resultOf(normal(1), normal(2), empty), MAX_QUESTIONS))
                .isInstanceOf(QuizGenerationFailedException.class);
    }
}
