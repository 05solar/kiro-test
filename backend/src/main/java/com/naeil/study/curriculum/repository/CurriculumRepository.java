package com.naeil.study.curriculum.repository;

import com.naeil.study.curriculum.entity.Curriculum;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurriculumRepository extends JpaRepository<Curriculum, UUID> {

    /** 세션의 학습 계획을 조회한다. 세션당 최대 하나다. */
    Optional<Curriculum> findByStudySessionId(UUID sessionId);

    /** 세션의 계획을 지운다. 단계({@code study_steps})를 먼저 지운 뒤에 호출한다. */
    void deleteAllByStudySessionId(UUID sessionId);
}
