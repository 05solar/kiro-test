package com.naeil.study.session.repository;

import com.naeil.study.session.entity.StudySession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {

    /** 8자리 코드로 세션을 조회한다. */
    Optional<StudySession> findBySessionCode(String sessionCode);

    /** 코드 발급 시 중복 여부를 확인한다. */
    boolean existsBySessionCode(String sessionCode);
}
