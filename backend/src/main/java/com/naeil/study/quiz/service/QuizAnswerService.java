package com.naeil.study.quiz.service;

import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.quiz.entity.QuizResult;
import com.naeil.study.quiz.exception.InvalidQuizOptionException;
import com.naeil.study.quiz.exception.QuizNotFoundException;
import com.naeil.study.quiz.repository.QuizRepository;
import com.naeil.study.quiz.repository.QuizResultRepository;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.exception.TopicNotFoundException;
import com.naeil.study.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 답안 제출(채점)과 결과 집계.
 *
 * <p>생성({@link QuizGenerationService})과 책임을 나눈다. 생성은 AI 를 부르고,
 * 채점은 DB 만 본다. 트랜잭션 정책도 다르다 — 채점에는 외부 호출이 없어
 * 통째로 트랜잭션 안에서 처리한다.
 *
 * <p><b>정답 판단은 서버가 한다.</b> 클라이언트가 보낸 정답 여부를 받지 않는다.
 *
 * <p><b>답안은 최초 1회만 저장한다.</b> 같은 문제에 다시 답하면 기존 결과를 그대로 돌려준다
 * ({@code UNIQUE(session_id, quiz_id)}). 정답을 본 뒤 답을 바꾸는 경로를 만들지 않는다.
 * 이 값이 이후 학습 성취도 판단의 근거이기 때문이다.
 */
@Service
@Transactional(readOnly = true)
public class QuizAnswerService {

    private static final Logger log = LoggerFactory.getLogger(QuizAnswerService.class);

    private final SessionService sessionService;
    private final TopicRepository topicRepository;
    private final QuizRepository quizRepository;
    private final QuizResultRepository quizResultRepository;
    private final Clock clock;

    public QuizAnswerService(
            SessionService sessionService,
            TopicRepository topicRepository,
            QuizRepository quizRepository,
            QuizResultRepository quizResultRepository,
            Clock clock
    ) {
        this.sessionService = sessionService;
        this.topicRepository = topicRepository;
        this.quizRepository = quizRepository;
        this.quizResultRepository = quizResultRepository;
        this.clock = clock;
    }

    /**
     * 답안 결과. 채점 후에는 정답과 해설을 공개해도 된다.
     *
     * @param alreadyAnswered 이번 요청이 아니라 이전에 답한 결과를 돌려준 경우 true
     */
    public record AnswerResult(Quiz quiz, QuizResult result, boolean alreadyAnswered) {
    }

    /**
     * Topic 의 채점 집계.
     *
     * <p>점수는 전체 문제 수 기준이다. 3문제만 풀고 2문제를 맞혔으면 40%다(2/5).
     * 최종 판단은 모든 문제를 푼 뒤({@code completed}) 한다.
     */
    public record TopicQuizResults(
            Topic topic,
            int totalQuestions,
            List<QuizResult> results
    ) {

        public int answeredQuestions() {
            return results.size();
        }

        public int correctAnswers() {
            return (int) results.stream().filter(QuizResult::isCorrect).count();
        }

        public int scorePercentage() {
            if (totalQuestions == 0) {
                return 0;
            }
            return (int) Math.round(correctAnswers() * 100.0 / totalQuestions);
        }

        public boolean completed() {
            return answeredQuestions() == totalQuestions;
        }
    }

    /**
     * 답안을 제출하고 채점한다.
     *
     * <p>이미 답한 문제면 <b>기존 결과를 그대로 돌려준다.</b> 새로고침이나 재전송으로
     * 같은 요청이 다시 올 수 있고, 그때 답이 바뀌면 채점 기록의 의미가 없어진다.
     *
     * @throws QuizNotFoundException      없거나 다른 세션의 문제
     * @throws InvalidQuizOptionException 보기 번호가 0~3 을 벗어남
     */
    @Transactional
    public AnswerResult answer(String sessionCode, UUID quizId, int selectedIndex) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        Quiz quiz = quizRepository.findByIdAndTopicStudySessionId(quizId, session.getId())
                .orElseThrow(QuizNotFoundException::new);

        if (selectedIndex < 0 || selectedIndex >= Quiz.OPTION_COUNT) {
            throw new InvalidQuizOptionException();
        }

        QuizResult existing = quizResultRepository
                .findByStudySessionIdAndQuizId(session.getId(), quiz.getId())
                .orElse(null);
        if (existing != null) {
            return new AnswerResult(quiz, existing, true);
        }

        boolean correct = quiz.isCorrectAnswer(selectedIndex);
        QuizResult result = quizResultRepository.save(QuizResult.create(
                session, quiz, selectedIndex, correct, LocalDateTime.now(clock)));

        log.info("quiz answered: sessionId={}, quizId={}, correct={}",
                session.getId(), quiz.getId(), correct);
        return new AnswerResult(quiz, result, false);
    }

    /**
     * Topic 의 채점 결과를 집계한다.
     *
     * @throws TopicNotFoundException 없거나 다른 세션의 Topic
     * @throws QuizNotFoundException  아직 퀴즈를 생성하지 않은 경우
     */
    @Transactional
    public TopicQuizResults results(String sessionCode, UUID topicId) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        Topic topic = topicRepository.findByIdAndStudySessionId(topicId, session.getId())
                .orElseThrow(TopicNotFoundException::new);

        /*
         * 마지막 회차만 센다.
         *
         * 사용자가 "새로운 퀴즈"를 만들면 같은 Topic 에 회차가 쌓인다. 전부 합치면
         * 2회차를 다 풀어도 "10문제 중 5문제"로 보이고 완료 판정도 나지 않는다.
         * 화면이 방금 푼 것은 마지막 회차다.
         */
        int latestRound = quizRepository.findLatestRound(topic.getId());
        if (latestRound == 0) {
            throw new QuizNotFoundException();
        }
        List<Quiz> quizzes =
                quizRepository.findAllByTopicIdAndRoundOrderByQuizOrderAsc(topic.getId(), latestRound);

        Set<UUID> quizIdsInRound = quizzes.stream().map(Quiz::getId).collect(Collectors.toSet());
        List<QuizResult> results = quizResultRepository
                .findAllByStudySessionIdAndQuizTopicId(session.getId(), topic.getId())
                .stream()
                .filter(result -> quizIdsInRound.contains(result.getQuiz().getId()))
                .toList();
        // 응답을 문제 순서대로 내보내기 위해 quizOrder 로 정렬한다
        Map<UUID, Integer> orderByQuizId = quizzes.stream()
                .collect(Collectors.toMap(Quiz::getId, Quiz::getQuizOrder));
        List<QuizResult> ordered = results.stream()
                .sorted(Comparator.comparingInt(result ->
                        orderByQuizId.getOrDefault(result.getQuiz().getId(), Integer.MAX_VALUE)))
                .toList();

        return new TopicQuizResults(topic, quizzes.size(), ordered);
    }
}
