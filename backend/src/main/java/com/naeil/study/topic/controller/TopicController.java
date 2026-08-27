package com.naeil.study.topic.controller;

import com.naeil.study.topic.dto.TopicListResponse;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.service.TopicService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Topic 조회 API.
 *
 * <p>개별 Topic 상세 API는 만들지 않는다. 목록 응답에 필요한 정보가 모두 들어 있다.
 */
@RestController
@RequestMapping("/api/sessions/{sessionCode}/topics")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    /** 분석된 Topic 목록을 조회한다. 아직 분석하지 않았으면 빈 배열이다. */
    @GetMapping
    public ResponseEntity<TopicListResponse> findAll(@PathVariable String sessionCode) {
        List<Topic> topics = topicService.findAll(sessionCode);
        return ResponseEntity.ok(TopicListResponse.from(topics));
    }
}
