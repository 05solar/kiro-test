package com.naeil.study.quiz.client.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * AI 구조화 응답의 문제 하나.
 *
 * <p>{@code correctIndex} 를 감싼 타입(Integer)으로 둔다. AI가 값을 빠뜨렸을 때
 * 0으로 조용히 채워져 "첫 번째 보기가 정답"이 되지 않고 검증 단계에서 드러나게 하기 위해서다.
 */
public record AiQuizQuestion(
        @JsonPropertyDescription("문제 본문. 강의자료에 근거한 내용만. 최대 2000자")
        String question,

        @JsonPropertyDescription("보기 정확히 4개. 서로 의미가 겹치지 않아야 하며 정답은 하나만 존재해야 함")
        List<String> options,

        @JsonPropertyDescription("정답 보기의 배열 인덱스. 0~3. 실제 정답과 반드시 일치해야 함")
        Integer correctIndex,

        @JsonPropertyDescription("정답 해설. 강의자료에서 확인 가능한 근거로만 작성")
        String explanation,

        @JsonPropertyDescription("난이도. EASY, MEDIUM, HARD 중 하나")
        String difficulty
) {
}
