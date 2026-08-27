package com.naeil.study.curriculum.repository;

import com.naeil.study.curriculum.entity.Curriculum;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurriculumRepository extends JpaRepository<Curriculum, UUID> {

    /** 세션의 학습 계획을 조회한다. 세션당 최대 하나다. */
    Optional<Curriculum> findByStudySessionId(UUID sessionId);
}
