package com.naeil.study.chat.dto;

import java.time.LocalDateTime;

/**
 * {@code POST /api/sessions/{sessionCode}/chat} 응답.
 *
 * @param answer               답변 본문
 * @param grounded             이 세션이 강의자료에 근거하는지
 * @param answeredFromMaterial 이 답변이 실제로 자료에서 확인된 내용인지.
 *                             자료가 있는 세션이라도 질문이 자료 밖이면 false 다.
 *                             화면은 이 값으로 "자료에 없어 일반 지식으로 답했다"를 알린다
 * @param answeredAt           답변 시각
 */
public record ChatResponse(
        String answer,
        boolean grounded,
        boolean answeredFromMaterial,
        LocalDateTime answeredAt
) {
}
