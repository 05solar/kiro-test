package com.naeil.study.quiz.repository;

import com.naeil.study.quiz.entity.Quiz;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    /** Topic 의 문제를 출제 순서대로 조회한다. */
    List<Quiz> findAllByTopicIdOrderByQuizOrderAsc(UUID topicId);

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
