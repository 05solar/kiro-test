package com.naeil.study.session.dto;

import com.naeil.study.session.entity.SessionStatus;
import com.naeil.study.session.entity.StudySession;
import java.time.LocalDateTime;

/**
 * {@code GET /api/sessions/{sessionCode}} 응답.
 *
 * <p>내부 식별자(UUID)는 포함하지 않는다. 사용자에게 노출되는 식별자는 세션 코드뿐이다.
 *
 * <p>{@code availableStudyMinutes}(최초 기준값)와 {@code remainingStudyMinutes}(현재 잔여)를
 * 모두 내려주므로 프론트엔드는 별도 계산 없이 소진량을 표시할 수 있다.
 */
public record SessionResponse(
        String sessionCode,
        String subject,
        LocalDateTime examAt,
        Integer availableStudyMinutes,
        Integer remainingStudyMinutes,
        SessionStatus status,
        Integer currentStepOrder,
        LocalDateTime createdAt,
        LocalDateTime lastAccessedAt,
        LocalDateTime expiresAt
) {

    public static SessionResponse from(StudySession session) {
        return new SessionResponse(
                session.getSessionCode(),
                session.getSubject(),
                session.getExamAt(),
                session.getAvailableStudyMinutes(),
                session.getRemainingStudyMinutes(),
                session.getStatus(),
                session.getCurrentStepOrder(),
                session.getCreatedAt(),
                session.getLastAccessedAt(),
                session.getExpiresAt()
        );
    }
}
