package com.naeil.study.quiz.repository;

import com.naeil.study.quiz.entity.Quiz;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    /** Topic 의 모든 회차 문제를 조회한다. 오답 요약처럼 전체가 필요할 때만 쓴다. */
    List<Quiz> findAllByTopicIdOrderByQuizOrderAsc(UUID topicId);

    /** 한 회차의 문제를 출제 순서대로 조회한다. 화면이 보는 것은 언제나 한 회차다. */
    List<Quiz> findAllByTopicIdAndRoundOrderByQuizOrderAsc(UUID topicId, int round);

    /**
     * 이 Topic 에서 지금까지 만든 마지막 회차. 아직 없으면 0.
     *
     * <p>다음 회차 번호를 정하고, 화면에 보여줄 현재 회차를 고르는 데 쓴다.
     */
    @Query("select coalesce(max(quiz.round), 0) from Quiz quiz where quiz.topic.id = :topicId")
    int findLatestRound(@Param("topicId") UUID topicId);

    /**
     * 중복 방지를 위해 이전에 낸 문제의 <b>문장만</b> 가져온다.
     *
     * <p>보기·정답·해설은 중복 판단에 필요 없다. 전부 보내면 회차가 쌓일수록
     * 프롬프트가 길어져 토큰만 늘어난다.
     */
    @Query("select quiz.question from Quiz quiz "
            + "where quiz.topic.id = :topicId "
            + "order by quiz.round asc, quiz.quizOrder asc")
    List<String> findQuestionsByTopicId(@Param("topicId") UUID topicId);

    /**
     * 세션의 모든 문제를 Topic 과 함께 조회한다. 오답 요약이 Topic 별 그룹화에 쓴다.
     *
     * <p>Topic 을 fetch join 한다. 트랜잭션 밖에서 Topic 의 제목·중요도를 읽기 때문이다.
     */
    @Query("select quiz from Quiz quiz "
            + "join fetch quiz.topic topic "
            + "where topic.studySession.id = :sessionId "
            + "order by topic.topicOrder asc, quiz.quizOrder asc")
    List<Quiz> findAllBySessionIdWithTopic(@Param("sessionId") UUID sessionId);

    /**
     * 세션이 소유한 문제를 조회한다.
     *
     * <p>문제 id만으로 찾지 않는다. Topic 을 거쳐 세션까지 함께 확인해야
     * 다른 세션의 문제에 답안을 낼 수 없다. 소유가 아니면 404로 다룬다.
     */
    Optional<Quiz> findByIdAndTopicStudySessionId(UUID id, UUID sessionId);
}
