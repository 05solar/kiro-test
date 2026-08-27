package com.naeil.study.analysis.validation;

import com.naeil.study.topic.entity.TopicImportance;
import java.util.List;
import java.util.UUID;

/**
 * 검증과 보정을 마친 Topic. 이 값만 DB에 저장한다.
 *
 * <p>AI가 돌려준 문서 참조값은 이미 실제 문서 UUID로 바뀐 상태다.
 *
 * @param topicOrder 배열에서의 순서. 1부터 시작한다
 */
public record ValidatedTopic(
        String title,
        String summary,
        List<String> keyPoints,
        TopicImportance importance,
        int estimatedStudyMinutes,
        boolean professorEmphasisMatched,
        boolean pastExamMatched,
        boolean weakAreaMatched,
        boolean mustStudyMatched,
        List<UUID> sourceDocumentIds,
        int topicOrder
) {
}
