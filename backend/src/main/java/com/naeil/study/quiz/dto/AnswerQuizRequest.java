package com.naeil.study.quiz.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 답안 제출 요청.
 *
 * <p>보내는 값은 선택한 보기 번호뿐이다. 정답 여부를 클라이언트가 계산해 보내는 구조를
 * 만들지 않는다. 범위(0~3) 검사는 서비스에서 하고 {@code INVALID_QUIZ_OPTION} 으로 응답한다.
 */
public record AnswerQuizRequest(
        @NotNull(message = "선택한 보기 번호를 입력해 주세요.")
        Integer selectedIndex
) {
}
