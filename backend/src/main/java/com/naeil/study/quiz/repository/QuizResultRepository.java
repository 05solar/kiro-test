package com.naeil.study.quiz.repository;

import com.naeil.study.quiz.entity.QuizResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizResultRepository extends JpaRepository<QuizResult, UUID> {

    /** 세션이 이 문제에 이미 낸 답안. 최초 1회만 저장되므로 최대 하나다. */
    Optional<QuizResult> findByStudySessionIdAndQuizId(UUID sessionId, UUID quizId);

    /** 세션이 한 Topic 의 문제들에 낸 답안 전체. 점수 집계에 쓴다. */
    List<QuizResult> findAllByStudySessionIdAndQuizTopicId(UUID sessionId, UUID topicId);

    /** 세션의 답안 전체. 오답 요약이 완료 여부 확인과 오답 추출에 쓴다. */
    List<QuizResult> findAllByStudySessionId(UUID sessionId);

    /** 세션의 답안을 모두 지운다. 재분석으로 퀴즈가 교체될 때 함께 사라진다. */
    void deleteAllByStudySessionId(UUID sessionId);
}
