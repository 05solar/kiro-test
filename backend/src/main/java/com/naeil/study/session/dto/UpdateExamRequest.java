package com.naeil.study.session.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * {@code PUT /api/sessions/{sessionCode}/exam} 요청.
 *
 * <p>같은 API를 다시 호출하면 시험 정보를 수정할 수 있다. 그래서 부분 수정(PATCH)이 아니라
 * 전체 교체(PUT)이며, 세 값이 모두 필수다.
 *
 * <p>{@code examAt}에는 {@code @Future}를 쓰지 않는다. 시간 판단은 주입한 {@code Clock}을
 * 쓰는 서비스에서 하고, 실패 시 {@code INVALID_EXAM_TIME}으로 응답한다.
 */
public record UpdateExamRequest(

        @NotBlank(message = "과목명을 입력해 주세요.")
        @Size(max = 100, message = "과목명은 100자 이하로 입력해 주세요.")
        String subject,

        @NotNull(message = "시험 일시를 입력해 주세요.")
        LocalDateTime examAt,

        @NotNull(message = "공부 가능한 시간을 입력해 주세요.")
        @Min(value = 1, message = "공부 가능한 시간은 1분 이상이어야 합니다.")
        @Max(value = 10080, message = "공부 가능한 시간은 10080분(7일) 이하로 입력해 주세요.")
        Integer availableStudyMinutes
) {
}
