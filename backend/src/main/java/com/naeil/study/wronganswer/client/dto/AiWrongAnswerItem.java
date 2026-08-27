package com.naeil.study.wronganswer.client.dto;

import java.util.List;

/**
 * AI에게 전달하는 오답 하나.
 *
 * <p>인덱스가 아니라 <b>보기의 실제 문자열</b>({@code userAnswer} / {@code correctAnswer})을
 * 전달한다. "2번을 골랐다"보다 "SJF 를 골랐는데 정답은 Round Robin"이 의미를 안정적으로
 * 전달한다. 변환은 서버가 한다.
 */
public record AiWrongAnswerItem(
        String question,
        List<String> options,
        String userAnswer,
        String correctAnswer,
        String explanation
) {
}
