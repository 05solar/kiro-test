package com.naeil.study.wronganswer.client.dto;

import java.util.List;

/**
 * AI에게 전달하는 Topic 하나의 오답 묶음.
 *
 * <p>{@code topicReference} 는 {@code TOPIC_1} 같은 참조값이다. AI 응답도 이 값으로
 * Topic 을 가리키고, 서버가 실제 UUID 로 되돌린다. AI 가 UUID 를 만들지 않는다.
 *
 * <p>{@code sourceContext} 는 이 Topic 의 출처 문서에서 추출한 관련 구간이다.
 * 복습 요약의 사실적 근거는 이것뿐이다.
 *
 * @param importance Topic 학습 우선순위 문자열. 복습 우선순위 판단 참고용
 */
public record AiWrongAnswerTopic(
        String topicReference,
        String topicTitle,
        String importance,
        boolean professorEmphasisMatched,
        boolean pastExamMatched,
        boolean weakAreaMatched,
        boolean mustStudyMatched,
        List<AiWrongAnswerItem> wrongAnswers,
        String sourceContext
) {
}
