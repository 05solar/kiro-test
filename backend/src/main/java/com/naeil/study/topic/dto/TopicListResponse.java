package com.naeil.study.topic.dto;

import com.naeil.study.topic.entity.Topic;
import java.util.List;

/** {@code GET /api/sessions/{sessionCode}/topics} 응답. {@code topicOrder} 순으로 정렬된다. */
public record TopicListResponse(List<TopicResponse> topics) {

    public static TopicListResponse from(List<Topic> topics) {
        return new TopicListResponse(topics.stream().map(TopicResponse::from).toList());
    }
}
