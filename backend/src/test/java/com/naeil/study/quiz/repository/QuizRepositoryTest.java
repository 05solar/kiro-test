package com.naeil.study.quiz.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.naeil.study.quiz.entity.Quiz;
import com.naeil.study.quiz.entity.QuizDifficulty;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 퀴즈 회차 영속화 검증.
 *
 * <p>실제 DB 에 붙여 확인한다. 유일 제약이 회차를 포함하는지는 목으로는 드러나지 않는다.
 * {@code (topic_id, quiz_order)} 로만 잠그면 2회차의 1번 문제가 1회차의 1번과 부딪혀
 * 저장에 실패하는데, 그건 사용자가 "새로운 퀴즈"를 누른 순간에야 나타난다.
 */
@DataJpaTest
@DisplayName("QuizRepository - 퀴즈 회차 영속화")
class QuizRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 12, 0, 0);

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private EntityManager entityManager;

    private Topic topic;

    @BeforeEach
    void setUp() {
        StudySession session = StudySession.create("QZ12RND4", NOW.minusHours(3), 30L);
        entityManager.persist(session);

        topic = Topic.create(session, "CPU 스케줄링", "요약", List.of("Round Robin"),
                TopicImportance.HIGH, 40, false, false, false, false,
                List.of(), 1, NOW.minusHours(1));
        entityManager.persist(topic);
        entityManager.flush();
    }

    private Quiz quiz(int round, int order) {
        return Quiz.create(topic, round, order, "%d회차 %d번 문제".formatted(round, order),
                List.of("A", "B", "C", "D"), 0, "해설", QuizDifficulty.MEDIUM, List.of(), NOW);
    }

    @Test
    @DisplayName("회차가 다르면 같은 순서 번호를 다시 쓸 수 있다")
    void allowsSameOrderInDifferentRound() {
        quizRepository.saveAllAndFlush(List.of(quiz(1, 1), quiz(1, 2)));

        // 여기서 터지면 "새로운 퀴즈 만들기"가 저장 단계에서 실패한다.
        assertThatCode(() -> quizRepository.saveAllAndFlush(List.of(quiz(2, 1), quiz(2, 2))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("같은 회차 안에서는 순서가 유일하다")
    void rejectsDuplicateOrderInSameRound() {
        quizRepository.saveAndFlush(quiz(1, 1));

        assertThatThrownBy(() -> quizRepository.saveAndFlush(quiz(1, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("마지막 회차를 찾는다. 없으면 0이다")
    void findsLatestRound() {
        assertThat(quizRepository.findLatestRound(topic.getId())).isZero();

        quizRepository.saveAllAndFlush(List.of(quiz(1, 1), quiz(2, 1), quiz(3, 1)));

        assertThat(quizRepository.findLatestRound(topic.getId())).isEqualTo(3);
    }

    @Test
    @DisplayName("한 회차의 문제만 순서대로 가져온다")
    void findsSingleRound() {
        quizRepository.saveAllAndFlush(List.of(quiz(1, 1), quiz(1, 2), quiz(2, 1)));

        List<Quiz> round2 =
                quizRepository.findAllByTopicIdAndRoundOrderByQuizOrderAsc(topic.getId(), 2);

        assertThat(round2).hasSize(1);
        assertThat(round2.get(0).getQuestion()).isEqualTo("2회차 1번 문제");
    }

    @Test
    @DisplayName("중복 방지용으로 모든 회차의 문제 문장만 가져온다")
    void findsAllQuestionTextsForDedup() {
        quizRepository.saveAllAndFlush(List.of(quiz(1, 1), quiz(1, 2), quiz(2, 1)));

        List<String> questions = quizRepository.findQuestionsByTopicId(topic.getId());

        // 회차 순, 그 안에서 출제 순. 보기·정답·해설은 가져오지 않는다.
        assertThat(questions)
                .containsExactly("1회차 1번 문제", "1회차 2번 문제", "2회차 1번 문제");
    }
}
