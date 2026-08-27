package com.naeil.study.quiz.validation;

import com.naeil.study.quiz.client.dto.AiQuizGenerationResult;
import com.naeil.study.quiz.client.dto.AiQuizQuestion;
import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.quiz.entity.QuizDifficulty;
import com.naeil.study.quiz.exception.QuizGenerationFailedException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * AI 퀴즈 응답을 검증한다.
 *
 * <p><b>AI 응답을 그대로 DB에 넣지 않는다.</b> 구조화 출력을 쓰더라도 보기 수, 정답 인덱스,
 * 난이도 같은 값의 규칙까지 보장되지는 않는다.
 *
 * <p>분석 검증기와 달리 <b>보정 없이 전부 실패로 처리한다.</b> 분석 결과는 값이 조금 벗어나도
 * 잘라서 쓸 수 있지만, 문제는 다르다. 보기가 3개인 문제나 정답 인덱스가 어긋난 문제를
 * "고쳐서" 내보내면 채점 자체가 틀어진다. 문제 하나라도 깨졌으면 전체를 다시 받는다.
 *
 * <pre>
 * 실패 조건
 *   - questions 가 비었다 / 3개 미만 / 요청 개수 초과
 *   - question 이 비었다 / 2000자 초과
 *   - options 가 4개가 아니다 / 빈 보기가 있다 / 정규화 후 중복 보기가 있다
 *   - correctIndex 가 없다 / 0~3 범위를 벗어난다
 *   - explanation 이 비었다
 *   - difficulty 가 EASY / MEDIUM / HARD 가 아니다
 * </pre>
 */
@Component
public class AiQuizResponseValidator {

    /** 근거가 부족하면 AI가 요청보다 적게 만들 수 있다. 그 하한이다. */
    public static final int MIN_QUESTIONS = 3;

    private static final int MAX_QUESTION_LENGTH = 2000;

    /**
     * AI 응답을 검증한다.
     *
     * @param maxQuestions 요청한 문제 수. 이보다 많으면 실패다
     * @throws QuizGenerationFailedException 규칙에 맞지 않는 경우
     */
    public List<ValidatedQuizQuestion> validate(AiQuizGenerationResult result, int maxQuestions) {
        if (result == null || result.questions() == null || result.questions().isEmpty()) {
            throw new QuizGenerationFailedException("ai returned no questions");
        }
        List<AiQuizQuestion> questions = result.questions();
        if (questions.size() < MIN_QUESTIONS || questions.size() > maxQuestions) {
            throw new QuizGenerationFailedException(
                    "question count out of range: " + questions.size()
                            + " (expected " + MIN_QUESTIONS + "~" + maxQuestions + ")");
        }

        List<ValidatedQuizQuestion> validated = new ArrayList<>(questions.size());
        for (int i = 0; i < questions.size(); i++) {
            validated.add(validateQuestion(questions.get(i), i + 1));
        }
        return validated;
    }

    private ValidatedQuizQuestion validateQuestion(AiQuizQuestion question, int order) {
        String text = requireText(question.question(), "question", order);
        if (text.length() > MAX_QUESTION_LENGTH) {
            throw new QuizGenerationFailedException(
                    "question too long at question " + order + ": " + text.length());
        }

        List<String> options = validateOptions(question.options(), order);

        if (question.correctIndex() == null) {
            throw new QuizGenerationFailedException("missing correctIndex at question " + order);
        }
        int correctIndex = question.correctIndex();
        if (correctIndex < 0 || correctIndex >= Quiz.OPTION_COUNT) {
            throw new QuizGenerationFailedException(
                    "correctIndex out of range at question " + order + ": " + correctIndex);
        }

        String explanation = requireText(question.explanation(), "explanation", order);

        QuizDifficulty difficulty = QuizDifficulty.from(question.difficulty())
                .orElseThrow(() -> new QuizGenerationFailedException(
                        "invalid difficulty at question " + order + ": " + question.difficulty()));

        return new ValidatedQuizQuestion(text, options, correctIndex, explanation, difficulty, order);
    }

    private List<String> validateOptions(List<String> options, int order) {
        if (options == null || options.size() != Quiz.OPTION_COUNT) {
            throw new QuizGenerationFailedException(
                    "options must have exactly " + Quiz.OPTION_COUNT + " at question " + order
                            + ": " + (options == null ? 0 : options.size()));
        }
        List<String> stripped = new ArrayList<>(Quiz.OPTION_COUNT);
        Set<String> normalized = new LinkedHashSet<>();
        for (String option : options) {
            if (option == null || option.isBlank()) {
                throw new QuizGenerationFailedException("blank option at question " + order);
            }
            stripped.add(option.strip());
            // 대소문자와 공백만 다른 보기는 사실상 같은 보기다. 그런 문제는 채점이 애매해진다.
            normalized.add(option.strip().toLowerCase(Locale.ROOT));
        }
        if (normalized.size() != Quiz.OPTION_COUNT) {
            throw new QuizGenerationFailedException("duplicated options at question " + order);
        }
        return List.copyOf(stripped);
    }

    private String requireText(String value, String field, int order) {
        if (value == null || value.isBlank()) {
            throw new QuizGenerationFailedException("blank " + field + " at question " + order);
        }
        return value.strip();
    }
}
