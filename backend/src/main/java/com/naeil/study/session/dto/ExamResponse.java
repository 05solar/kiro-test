package com.naeil.study.session.dto;

import com.naeil.study.session.entity.SessionStatus;
import com.naeil.study.session.entity.StudySession;
import java.time.LocalDateTime;

/**
 * {@code PUT /api/sessions/{sessionCode}/exam} 응답.
 *
 * <p>저장한 시험 범위도 함께 돌려준다. 강의자료를 올리지 않을 경우 이 값이 학습 내용을
 * 만드는 유일한 근거가 되므로, 무엇이 저장됐는지 사용자가 확인할 수 있어야 한다.
 *
 * <p>{@code availableStudyMinutes}는 사용자가 입력한 원본값이고,
 * {@code remainingStudyMinutes}는 시험까지 남은 실제 시간을 반영해 서버가 계산한 값이다.
 * 두 값이 다를 수 있으므로 저장 결과를 그대로 돌려주어 사용자가 확인할 수 있게 한다.
 */
public record ExamResponse(
        String sessionCode,
        String subject,
        String examScope,
        LocalDateTime examAt,
        Integer availableStudyMinutes,
        Integer remainingStudyMinutes,
        SessionStatus status
) {

    public static ExamResponse from(StudySession session) {
        return new ExamResponse(
                session.getSessionCode(),
                session.getSubject(),
                session.getExamScope(),
                session.getExamAt(),
                session.getAvailableStudyMinutes(),
                session.getRemainingStudyMinutes(),
                session.getStatus()
        );
    }
}
