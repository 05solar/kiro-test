package com.naeil.study.wronganswer.entity;

import java.util.List;
import java.util.UUID;

/**
 * Topic 하나의 복습 요약. {@link WrongAnswerSummary} 의 jsonb 컬럼 항목이다.
 *
 * <p>별도 테이블로 만들지 않는다. 요약은 통째로 생성되고 통째로 교체되는 값이라
 * 항목 단위로 조회하거나 수정할 일이 없다.
 *
 * <p>{@code topicId} 는 서버가 참조값(TOPIC_n)을 실제 UUID 로 바꿔 넣은 값이다.
 * AI 가 UUID 를 만들지 않는다.
 *
 * @param wrongConcepts   사용자가 틀린 개념 이름들
 * @param keyReviewPoints 반드시 기억할 포인트들
 */
public record TopicReviewSnapshot(
        UUID topicId,
        String topicTitle,
        List<String> wrongConcepts,
        String summary,
        List<String> keyReviewPoints,
        ReviewPriority priority
) {
}
