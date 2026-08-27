package com.naeil.study.studycontext.dto;

import com.naeil.study.studycontext.entity.StudyContextPolicy;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/sessions/{sessionCode}/study-context} 요청.
 *
 * <p>네 항목 모두 선택 입력이다. 전부 {@code null}인 요청도 정상 처리한다.
 * 사용자가 추가 정보를 주지 않아도 이후 AI 기능은 동작해야 하기 때문이다.
 *
 * <p>PUT이므로 부분 수정이 아니라 전체 교체다. 보내지 않은 항목은 비워진다.
 */
public record UpdateStudyContextRequest(

        @Size(max = StudyContextPolicy.MAX_FIELD_LENGTH, message = "교수님 강조 내용은 2000자 이하로 입력해 주세요.")
        String professorEmphasis,

        @Size(max = StudyContextPolicy.MAX_FIELD_LENGTH, message = "기출/예상 문제는 2000자 이하로 입력해 주세요.")
        String pastExamInfo,

        @Size(max = StudyContextPolicy.MAX_FIELD_LENGTH, message = "자신 없는 부분은 2000자 이하로 입력해 주세요.")
        String weakAreas,

        @Size(max = StudyContextPolicy.MAX_FIELD_LENGTH, message = "반드시 공부할 범위는 2000자 이하로 입력해 주세요.")
        String mustStudyAreas
) {
}
