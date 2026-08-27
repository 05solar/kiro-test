package com.naeil.study.topic.dto;

import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import java.util.List;
import java.util.UUID;

/**
 * Topic 한 건의 응답.
 *
 * <p>{@code estimatedStudyMinutes}는 남은 시간에 맞춘 조정 전 값이다.
 * 전체 합이 사용자의 남은 학습 시간을 넘을 수 있으며, 조정은 커리큘럼 단계에서 한다.
 */
public record TopicResponse(
        UUID id,
        String title,
        String summary,
        List<String> keyPoints,
        TopicImportance importance,
        int estimatedStudyMinutes,
        boolean professorEmphasisMatched,
        boolean pastExamMatched,
        boolean weakAreaMatched,
        boolean mustStudyMatched,
        int topicOrder
) {

    public static TopicResponse from(Topic topic) {
        return new TopicResponse(
                topic.getId(),
                topic.getTitle(),
                topic.getSummary(),
                topic.getKeyPoints(),
                topic.getImportance(),
                topic.getEstimatedStudyMinutes(),
                topic.isProfessorEmphasisMatched(),
                topic.isPastExamMatched(),
                topic.isWeakAreaMatched(),
                topic.isMustStudyMatched(),
                topic.getTopicOrder()
        );
    }
}
