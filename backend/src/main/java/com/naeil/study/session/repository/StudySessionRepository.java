package com.naeil.study.session.repository;

import com.naeil.study.session.entity.StudySession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {

    /** 8자리 코드로 세션을 조회한다. */
    Optional<StudySession> findBySessionCode(String sessionCode);

    /** 코드 발급 시 중복 여부를 확인한다. */
    boolean existsBySessionCode(String sessionCode);

    /**
     * 보관 기한이 지난 세션을 오래된 것부터 조회한다.
     *
     * <p>한 번에 가져오는 개수를 {@link Pageable} 로 제한한다. 오래 방치된 환경에서
     * 첫 정리가 수만 건을 한 트랜잭션으로 처리하면 DB가 오래 잠긴다.
     * 남은 것은 다음 주기에 지운다.
     */
    List<StudySession> findByExpiresAtBeforeOrderByExpiresAtAsc(LocalDateTime now, Pageable pageable);
}
