package com.naeil.study.topic.service;

import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.repository.TopicRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Topic 조회 유스케이스.
 *
 * <p>Topic을 만드는 쪽은 분석 도메인이다. 이 서비스는 읽기만 담당한다.
 */
@Service
@Transactional(readOnly = true)
public class TopicService {

    private final TopicRepository topicRepository;
    private final SessionService sessionService;

    public TopicService(TopicRepository topicRepository, SessionService sessionService) {
        this.topicRepository = topicRepository;
        this.sessionService = sessionService;
    }

    /** 세션의 Topic을 기본 학습 순서대로 조회한다. 아직 분석하지 않았으면 빈 목록이다. */
    @Transactional
    public List<Topic> findAll(String sessionCode) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        return topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(session.getId());
    }
}
