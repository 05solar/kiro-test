package com.naeil.study.wronganswer.client.dto;

import com.naeil.study.analysis.client.dto.AiStudyContext;
import java.util.List;

/**
 * AI 오답 요약 생성 요청.
 *
 * <p>오답만 담는다. 맞힌 문제는 복습 대상이 아니므로 요청에 포함하지 않는다.
 * 학습 맥락({@code studyContext})은 무엇을 더 강조해 복습할지 정하는 힌트일 뿐,
 * 사실의 출처가 아니다.
 */
public record AiWrongAnswerSummaryRequest(
        String subject,
        AiStudyContext studyContext,
        List<AiWrongAnswerTopic> topics
) {
}
