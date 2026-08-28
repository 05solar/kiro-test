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
import java.util.regex.Pattern;
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

    /*
     * 프롬프트가 새어 나온 흔적을 지운다.
     *
     * 시스템 프롬프트가 <lecture_context> 같은 태그로 자료를 구분하는데, 모델이 그 이름을
     * 문제 본문에 그대로 쓰는 일이 실제로 있었다.
     *
     *   "<lecture_context>에 근거할 때, 요구 사항 도출 단계에서 ... 옳은 것은?"
     *
     * 학생은 그런 태그를 본 적이 없다. 시험지에 실릴 문장에 남아서는 안 된다.
     * 프롬프트로도 막지만(QuizPrompts), 모델이 규칙을 어기는 것은 언제든 일어난다.
     * 마지막에 서버가 걷어 낸다.
     */

    /**
     * 프롬프트의 자료 구분 태그.
     *
     * <p>문두 머리말이 아닌 자리에 있으면 <b>지우지 않고 "자료"로 바꾼다.</b> 태그가 명사
     * 자리를 차지하고 있기 때문이다. 지우면 문장이 깨진다.
     *
     * <pre>
     * "...협력하는 대상으로 &lt;lecture_context&gt;에서 언급된 것은?"
     *   지우면 → "...협력하는 대상으로 에서 언급된 것은?"   (깨진다)
     *   바꾸면 → "...협력하는 대상으로 자료에서 언급된 것은?" (읽힌다)
     * </pre>
     */
    private static final Pattern PROMPT_TAG = Pattern.compile("</?[a-z][a-z0-9_]*>");

    /** 태그가 가리키던 것. 학생이 아는 말로 바꾼다. */
    private static final String TAG_REPLACEMENT = "자료";

    /**
     * 문두에 붙는 출처 머리말.
     *
     * <p>"자료" 뒤에 조사가 바로 오는 경우만 잡는다. 그래야 "자료구조에 근거할 때"처럼
     * 과목명으로 시작하는 정상 문장을 지우지 않는다.
     */
    private static final Pattern SOURCE_PREFIX = Pattern.compile(
            "^\\s*(?:다음\\s*)?"
                    + "(?:제공된\\s*|주어진\\s*|위\\s*|해당\\s*)?"
                    // 태그 이름 자체가 출처 자리에 오는 경우가 실제 사례다.
                    + "(?:</?[a-z][a-z0-9_]*>|강의\\s*자료|학습\\s*자료|수업\\s*자료|자료|지문|본문|문서|내용)"
                    + "\\s*(?:에서|에|을|를)?\\s*"
                    + "(?:근거할\\s*때|근거하여|근거해서|근거해|바탕으로|기반으로|기초하여"
                    + "|따르면|의하면|제시된\\s*바에\\s*따르면)"
                    // "…를 바탕으로 할 때," 처럼 뒤에 한 마디가 더 붙는 경우가 있다.
                    + "\\s*(?:할\\s*때|하면|보면|살펴보면)?"
                    + "\\s*[,:]?\\s*");

    /**
     * 태그가 먼저 지워진 뒤 문두에 남는 조사 잔해.
     *
     * <p>"…에 근거할 때," 처럼 가리키는 말이 사라진 껍데기다. 문장으로 성립하지 않는다.
     */
    private static final Pattern ORPHAN_PREFIX = Pattern.compile(
            "^\\s*(?:에서|에|을|를)\\s*"
                    + "(?:근거할\\s*때|근거하여|근거해서|근거해|바탕으로|기반으로|기초하여|따르면|의하면)"
                    + "\\s*[,:]?\\s*");

    /** 태그를 지우고 남은 빈 껍데기. "( )" 나 "<>" 같은 것. */
    private static final Pattern EMPTY_BRACKETS = Pattern.compile("\\(\\s*\\)|\\[\\s*\\]|<\\s*>");

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
        String text = stripPromptLeakage(
                requireText(question.question(), "question", order), "question", order);
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

        String explanation = stripPromptLeakage(
                requireText(question.explanation(), "explanation", order), "explanation", order);

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
            // 보기에서는 머리말을 벗기지 않는다. 보기 내용 자체를 훼손할 수 있다.
            // 태그 이름만 "자료"로 바꾼다 — 그건 어떤 보기에도 들어갈 이유가 없다.
            String cleaned = PROMPT_TAG.matcher(option).replaceAll(TAG_REPLACEMENT).strip();
            if (cleaned.isBlank()) {
                throw new QuizGenerationFailedException("blank option at question " + order);
            }
            stripped.add(cleaned);
            // 대소문자와 공백만 다른 보기는 사실상 같은 보기다. 그런 문제는 채점이 애매해진다.
            // 태그를 지운 뒤의 값으로 본다 — 태그만 다른 두 보기는 학생에게 똑같이 보인다.
            normalized.add(cleaned.toLowerCase(Locale.ROOT));
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

    /**
     * 문제·해설에서 프롬프트가 새어 나온 부분을 걷어 낸다.
     *
     * <p>머리말은 <b>문두에서만</b> 지운다. 문장 중간의 "자료에 따르면"은 설명의 일부일 수
     * 있어 건드리지 않는다. 태그 이름은 어디에 있든 지운다 — 그건 어떤 경우에도 새어 나온 것이다.
     *
     * <p>지우고 나면 빈 문자열이 될 수 있다. 그때는 고쳐 쓰지 않고 실패시킨다.
     * 머리말만 있고 묻는 내용이 없는 문제는 애초에 문제가 아니다.
     */
    private String stripPromptLeakage(String text, String field, int order) {
        /*
         * 머리말을 먼저 벗긴다. 순서가 중요하다.
         *
         * 태그를 먼저 지우면 "<lecture_context>에 근거할 때," 가 "에 근거할 때," 로 남는데,
         * 그때는 무엇을 가리키던 말인지 알 수 없어 머리말로 인식되지 않는다.
         */
        String cleaned = text;
        // 머리말이 겹쳐 붙는 경우가 있다. 두 번까지만 벗긴다 — 그 이상은 정상 문장이 아니다.
        for (int i = 0; i < 2; i++) {
            String stripped = SOURCE_PREFIX.matcher(cleaned).replaceFirst("");
            if (stripped.equals(cleaned)) {
                break;
            }
            cleaned = stripped;
        }

        // 남은 태그는 문장 어디에 있든 지운다.
        cleaned = PROMPT_TAG.matcher(cleaned).replaceAll(TAG_REPLACEMENT);
        // 그러고도 조사만 남았다면 그것도 껍데기다.
        cleaned = ORPHAN_PREFIX.matcher(cleaned).replaceFirst("");
        cleaned = EMPTY_BRACKETS.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("\\s{2,}", " ").strip();

        if (cleaned.isBlank()) {
            throw new QuizGenerationFailedException(
                    "only prompt leakage in " + field + " at question " + order);
        }
        return cleaned;
    }
}
