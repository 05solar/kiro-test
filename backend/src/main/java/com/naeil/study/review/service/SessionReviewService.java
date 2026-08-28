package com.naeil.study.review.service;

import com.naeil.study.curriculum.entity.Curriculum;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.entity.StudyStepStatus;
import com.naeil.study.curriculum.exception.CurriculumNotFoundException;
import com.naeil.study.curriculum.repository.CurriculumRepository;
import com.naeil.study.curriculum.repository.StudyStepRepository;
import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.quiz.entity.QuizResult;
import com.naeil.study.quiz.repository.QuizRepository;
import com.naeil.study.quiz.repository.QuizResultRepository;
import com.naeil.study.quiz.service.QuizAnswerService.QuizReviewItem;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.topic.entity.Topic;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 전체 정리.
 *
 * <p>공부가 끝난 뒤 한 번에 훑어보는 화면을 위한 것이다. 스텝별 요약, 푼 문제 전체,
 * 틀린 문제만 — 세 가지를 보여 주는데, 셋 다 같은 자료에서 나온다. 그래서 화면이
 * 세 번 묻지 않고 한 번에 받아 가도록 한 응답에 담는다.
 *
 * <p><b>AI 를 부르지 않는다.</b> 요약은 분석 단계에서 이미 만들어 {@link Topic} 에
 * 저장해 둔 것을 그대로 쓰고, 문제와 답안도 DB 에 있다. 정리 화면을 열 때마다 요약을
 * 새로 생성하면 볼 때마다 과금되고, 같은 내용이 매번 달라져 "정리"가 되지 않는다.
 *
 * <p>도메인을 따로 둔 이유는 이 화면이 curriculum·topic·quiz 세 도메인을 가로지르기
 * 때문이다. 어느 한쪽에 넣으면 그 도메인이 다른 둘을 알게 된다.
 */
@Service
@Transactional(readOnly = true)
public class SessionReviewService {

    private final SessionService sessionService;
    private final CurriculumRepository curriculumRepository;
    private final StudyStepRepository studyStepRepository;
    private final QuizRepository quizRepository;
    private final QuizResultRepository quizResultRepository;

    public SessionReviewService(
            SessionService sessionService,
            CurriculumRepository curriculumRepository,
            StudyStepRepository studyStepRepository,
            QuizRepository quizRepository,
            QuizResultRepository quizResultRepository
    ) {
        this.sessionService = sessionService;
        this.curriculumRepository = curriculumRepository;
        this.studyStepRepository = studyStepRepository;
        this.quizRepository = quizRepository;
        this.quizResultRepository = quizResultRepository;
    }

    /**
     * 스텝 하나의 정리.
     *
     * @param topic 스텝에 붙은 Topic. 휴식 같은 Topic 없는 스텝이면 {@code null}
     * @param round 마지막 퀴즈 회차. 퀴즈를 만든 적 없으면 0
     * @param items 마지막 회차의 문제와 답안. 퀴즈가 없으면 빈 목록
     */
    public record StepReview(StudyStep step, Topic topic, int round, List<QuizReviewItem> items) {

        public int answeredQuestions() {
            return (int) items.stream().filter(QuizReviewItem::answered).count();
        }

        public int wrongQuestions() {
            return (int) items.stream().filter(QuizReviewItem::wrong).count();
        }
    }

    /**
     * 세션 전체 정리.
     *
     * @param completed 모든 스텝을 마쳤는가. 건너뛴 스텝은 남은 것으로 세지 않는다 —
     *                  시간이 모자라 잘라 낸 스텝까지 끝내야 완료라면 영영 완료가 되지 않는다
     */
    public record SessionReview(
            StudySession session,
            Curriculum curriculum,
            List<StepReview> steps
    ) {

        public int totalSteps() {
            return steps.size();
        }

        public int completedSteps() {
            return (int) steps.stream()
                    .filter(step -> step.step().getStatus() == StudyStepStatus.COMPLETED)
                    .count();
        }

        public boolean completed() {
            return steps.stream()
                    .noneMatch(step -> step.step().getStatus() == StudyStepStatus.PENDING
                            || step.step().getStatus() == StudyStepStatus.IN_PROGRESS);
        }

        public int totalQuestions() {
            return steps.stream().mapToInt(step -> step.items().size()).sum();
        }

        public int answeredQuestions() {
            return steps.stream().mapToInt(StepReview::answeredQuestions).sum();
        }

        public int wrongAnswers() {
            return steps.stream().mapToInt(StepReview::wrongQuestions).sum();
        }

        public int correctAnswers() {
            return answeredQuestions() - wrongAnswers();
        }

        /** 푼 문제 기준 정답률. 안 푼 문제는 분모에서 뺀다 — 정리 화면은 실제로 푼 것을 평가한다. */
        public int scorePercentage() {
            int answered = answeredQuestions();
            if (answered == 0) {
                return 0;
            }
            return (int) Math.round(correctAnswers() * 100.0 / answered);
        }
    }

    /**
     * 세션 전체 정리를 모은다.
     *
     * <p>완료 여부로 막지 않는다. 아직 진행 중이어도 지금까지의 정리는 볼 수 있어야 한다.
     * "다 끝냈을 때만 보여 준다"는 판단은 {@code completed} 를 보고 화면이 한다.
     *
     * @throws CurriculumNotFoundException 아직 학습 계획을 만들지 않은 경우
     */
    @Transactional
    public SessionReview review(String sessionCode) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        Curriculum curriculum = curriculumRepository.findByStudySessionId(session.getId())
                .orElseThrow(CurriculumNotFoundException::new);
        List<StudyStep> steps =
                studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(curriculum.getId());

        /*
         * 세션의 퀴즈와 답안을 한 번에 읽고 메모리에서 짝지운다.
         *
         * 스텝마다 따로 물으면 스텝 수만큼 질의가 나간다(N+1). 스텝은 보통 열 개 안팎이지만
         * 이 화면은 그 전부를 한 번에 그리므로, 여기서만큼은 한 번에 읽는 편이 낫다.
         */
        Map<UUID, List<Quiz>> quizzesByTopicId = quizRepository
                .findAllBySessionIdWithTopic(session.getId())
                .stream()
                .collect(Collectors.groupingBy(quiz -> quiz.getTopic().getId()));
        Map<UUID, QuizResult> resultByQuizId = quizResultRepository
                .findAllByStudySessionId(session.getId())
                .stream()
                .collect(Collectors.toMap(result -> result.getQuiz().getId(), result -> result));

        List<StepReview> stepReviews = steps.stream()
                .map(step -> toStepReview(step, quizzesByTopicId, resultByQuizId))
                .toList();

        return new SessionReview(session, curriculum, stepReviews);
    }

    private StepReview toStepReview(
            StudyStep step,
            Map<UUID, List<Quiz>> quizzesByTopicId,
            Map<UUID, QuizResult> resultByQuizId
    ) {
        Topic topic = step.getTopic();
        if (topic == null) {
            return new StepReview(step, null, 0, List.of());
        }

        List<Quiz> all = quizzesByTopicId.getOrDefault(topic.getId(), List.of());
        if (all.isEmpty()) {
            return new StepReview(step, topic, 0, List.of());
        }

        /*
         * 마지막 회차만 본다.
         *
         * "새로운 퀴즈"를 만들면 같은 Topic 에 회차가 쌓인다. 전부 합치면 정리 화면에
         * 같은 범위의 문제가 두 벌 나오고, 문제 수도 실제로 푼 것보다 부풀어 보인다.
         */
        int latestRound = all.stream().mapToInt(Quiz::getRound).max().orElse(0);
        List<QuizReviewItem> items = all.stream()
                .filter(quiz -> quiz.getRound() == latestRound)
                .sorted(Comparator.comparingInt(Quiz::getQuizOrder))
                .map(quiz -> new QuizReviewItem(quiz, resultByQuizId.get(quiz.getId())))
                .toList();

        return new StepReview(step, topic, latestRound, items);
    }
}
