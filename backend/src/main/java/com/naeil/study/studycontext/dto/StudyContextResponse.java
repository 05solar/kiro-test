package com.naeil.study.studycontext.dto;

import com.naeil.study.studycontext.entity.StudyContext;
import java.time.LocalDateTime;

/**
 * 학습 맥락 응답.
 *
 * <p>아직 입력하지 않은 세션도 404가 아니라 200에 전부 {@code null}로 응답한다.
 * 학습 맥락은 선택 입력이라 "없음"이 정상 상태이고, 프론트가 예외 처리 없이
 * 입력 화면을 초기화할 수 있어야 한다.
 */
public record StudyContextResponse(
        String sessionCode,
        String professorEmphasis,
        String pastExamInfo,
        String weakAreas,
        String mustStudyAreas,
        LocalDateTime updatedAt
) {

    public static StudyContextResponse from(String sessionCode, StudyContext studyContext) {
        return new StudyContextResponse(
                sessionCode,
                studyContext.getProfessorEmphasis(),
                studyContext.getPastExamInfo(),
                studyContext.getWeakAreas(),
                studyContext.getMustStudyAreas(),
                studyContext.getUpdatedAt()
        );
    }

    /** 아직 학습 맥락을 입력하지 않은 세션의 응답. */
    public static StudyContextResponse empty(String sessionCode) {
        return new StudyContextResponse(sessionCode, null, null, null, null, null);
    }
}
