package com.naeil.study.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/sessions/{sessionCode}/chat} 요청.
 *
 * <p>지난 대화를 보내지 않는다. 서버가 갖고 있다. 클라이언트가 보내오게 두면 없던 발화를
 * 지어내 프롬프트에 넣을 수 있다.
 *
 * <p>길이를 제한하는 것은 비용 때문만이 아니다. 자료 한 편을 통째로 붙여 넣고 "요약해 줘"를
 * 하면 그 텍스트가 그대로 프롬프트에 들어간다. 그 용도는 강의자료 업로드가 담당한다.
 */
public record ChatRequest(

        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = 1000, message = "질문은 1000자 이하로 입력해 주세요.")
        String message
) {
}
