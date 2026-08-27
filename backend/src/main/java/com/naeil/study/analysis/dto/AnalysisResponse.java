package com.naeil.study.analysis.dto;

import com.naeil.study.session.entity.SessionStatus;

/**
 * {@code POST /api/sessions/{sessionCode}/analysis} 응답.
 *
 * <p>동기로 처리하므로 응답 시점에 이미 분석이 끝나 있다. 그래서 진행 중 상태가 아니라
 * 최종 상태({@code READY})와 만들어진 Topic 수를 돌려준다.
 *
 * <p>Topic 내용은 담지 않는다. 목록은 {@code GET .../topics} 로 조회한다.
 */
public record AnalysisResponse(String sessionCode, SessionStatus status, int topicCount) {

    public static AnalysisResponse of(String sessionCode, int topicCount) {
        return new AnalysisResponse(sessionCode, SessionStatus.READY, topicCount);
    }
}
