package com.naeil.study.studycontext.repository;

import com.naeil.study.studycontext.entity.StudyContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyContextRepository extends JpaRepository<StudyContext, UUID> {

    /**
     * 세션의 학습 맥락을 조회한다.
     *
     * <p>한 세션에 최대 하나만 존재하므로 {@link Optional}을 돌려준다.
     * 조회 조건은 항상 세션 ID다. 다른 세션의 맥락이 넘어올 경로를 만들지 않는다.
     */
    Optional<StudyContext> findByStudySessionId(UUID sessionId);
}
