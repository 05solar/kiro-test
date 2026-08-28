package com.naeil.study.review.controller;

import com.naeil.study.review.dto.SessionReviewResponse;
import com.naeil.study.review.service.SessionReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세션 전체 정리 API.
 *
 * <p>공부를 마친 뒤 한 번에 훑어보는 화면이 쓴다. 스텝별 요약, 푼 문제 전체, 틀린 문제만 —
 * 세 화면이 이 응답 하나를 나눠 쓴다.
 *
 * <p>AI 를 부르지 않는다. 이미 저장해 둔 요약과 답안을 모아 줄 뿐이다.
 */
@RestController
@RequestMapping("/api/sessions/{sessionCode}")
public class SessionReviewController {

    private final SessionReviewService sessionReviewService;

    public SessionReviewController(SessionReviewService sessionReviewService) {
        this.sessionReviewService = sessionReviewService;
    }

    /**
     * 세션 전체 정리를 조회한다. 아직 학습 계획을 만들지 않았으면 404다.
     *
     * <p>모든 스텝을 마쳤는지로 막지 않는다. 진행 중이어도 지금까지의 정리는 볼 수 있고,
     * 정리 기능을 언제 열지는 {@code completed} 를 보고 화면이 정한다.
     */
    @GetMapping("/review")
    public ResponseEntity<SessionReviewResponse> review(@PathVariable String sessionCode) {
        return ResponseEntity.ok(
                SessionReviewResponse.from(sessionReviewService.review(sessionCode)));
    }
}
