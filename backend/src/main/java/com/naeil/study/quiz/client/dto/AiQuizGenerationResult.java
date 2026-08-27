package com.naeil.study.quiz.client.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * AI 퀴즈 생성의 구조화 응답 전체.
 */
public record AiQuizGenerationResult(
        @JsonPropertyDescription("생성한 4지선다 문제 목록")
        List<AiQuizQuestion> questions
) {
}
