package com.naeil.study.session.dto;

import com.naeil.study.session.entity.SessionStatus;
import com.naeil.study.session.entity.StudySession;

/**
 * {@code POST /api/sessions} 응답.
 *
 * <p>세션 생성 직후에는 사용자가 기억해야 할 코드와 상태만 돌려준다.
 */
public record CreateSessionResponse(String sessionCode, SessionStatus status) {

    public static CreateSessionResponse from(StudySession session) {
        return new CreateSessionResponse(session.getSessionCode(), session.getStatus());
    }
}
