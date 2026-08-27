package com.naeil.study.topic.repository;

import com.naeil.study.topic.entity.Topic;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

    /** 세션의 Topic을 기본 학습 순서대로 조회한다. */
    List<Topic> findAllByStudySessionIdOrderByTopicOrderAsc(UUID sessionId);

    /**
     * 세션이 소유한 Topic을 조회한다.
     *
     * <p>Topic id만으로 찾지 않는다. 다른 세션의 Topic id를 넣어도 조회되지 않아야 한다.
     */
    Optional<Topic> findByIdAndStudySessionId(UUID id, UUID sessionId);

    /**
     * 세션의 Topic을 모두 지운다. 재분석 시 기존 결과를 교체하기 위해 쓴다.
     *
     * <p>분석 결과는 통째로 갈아 끼우는 값이라 개별 갱신을 하지 않는다.
     */
    void deleteAllByStudySessionId(UUID sessionId);
}
