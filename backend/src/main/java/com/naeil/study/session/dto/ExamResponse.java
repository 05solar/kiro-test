package com.naeil.study.session.dto;

import com.naeil.study.session.entity.SessionStatus;
import com.naeil.study.session.entity.StudySession;
import java.time.LocalDateTime;

/**
 * {@code PUT /api/sessions/{sessionCode}/exam} 응답.
 *
 * <p>{@code availableStudyMinutes}는 사용자가 입력한 원본값이고,
 * {@code remainingStudyMinutes}는 시험까지 남은 실제 시간을 반영해 서버가 계산한 값이다.
 * 두 값이 다를 수 있으므로 저장 결과를 그대로 돌려주어 사용자가 확인할 수 있게 한다.
 */
public record ExamResponse(
        String sessionCode,
        String subject,
        LocalDateTime examAt,
        Integer availableStudyMinutes,
        Integer remainingStudyMinutes,
        SessionStatus status
) {

    public static ExamResponse from(StudySession session) {
        return new ExamResponse(
                session.getSessionCode(),
                session.getSubject(),
                session.getExamAt(),
                session.getAvailableStudyMinutes(),
                session.getRemainingStudyMinutes(),
                session.getStatus()
        );
    }
}
