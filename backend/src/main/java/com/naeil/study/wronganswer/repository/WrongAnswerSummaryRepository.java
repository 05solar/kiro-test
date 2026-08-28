package com.naeil.study.wronganswer.repository;

import com.naeil.study.wronganswer.entity.WrongAnswerSummary;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WrongAnswerSummaryRepository extends JpaRepository<WrongAnswerSummary, UUID> {

    /** 세션의 현재 복습 요약을 조회한다. 세션당 최대 하나다. */
    Optional<WrongAnswerSummary> findByStudySessionId(UUID sessionId);

    /** 세션의 요약을 지운다. 재분석으로 옛 오답 기록이 무효화될 때 쓴다. */
    void deleteAllByStudySessionId(UUID sessionId);
}
