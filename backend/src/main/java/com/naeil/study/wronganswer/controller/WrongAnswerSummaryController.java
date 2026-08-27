package com.naeil.study.wronganswer.controller;

import com.naeil.study.wronganswer.dto.WrongAnswerSummaryResponse;
import com.naeil.study.wronganswer.service.WrongAnswerSummaryService;
import com.naeil.study.wronganswer.service.WrongAnswerSummaryService.SummaryOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오답 복습 요약 API.
 *
 * <p>Request Body 가 없다. 오답과 강의자료는 이미 서버 DB 에 있고, 프론트가 채점 결과를
 * 다시 보내는 구조를 만들지 않는다.
 */
@RestController
@RequestMapping("/api/sessions/{sessionCode}/wrong-answer-summary")
public class WrongAnswerSummaryController {

    private final WrongAnswerSummaryService summaryService;

    public WrongAnswerSummaryController(WrongAnswerSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    /**
     * 세션 전체 오답으로 복습 요약을 만든다.
     *
     * <p>이번 요청에서 AI 로 새로 생성(재생성 포함)했을 때만 201이다.
     * 캐시된 기존 요약을 돌려주거나 오답이 없어 만들 것이 없으면 200이다.
     */
    @PostMapping
    public ResponseEntity<WrongAnswerSummaryResponse> generate(@PathVariable String sessionCode) {
        SummaryOutcome outcome = summaryService.generate(sessionCode);
        HttpStatus status = outcome.generated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(WrongAnswerSummaryResponse.from(outcome));
    }

    /** 저장된 복습 요약을 조회한다. 아직 생성하지 않았으면 404다. */
    @GetMapping
    public ResponseEntity<WrongAnswerSummaryResponse> find(@PathVariable String sessionCode) {
        return ResponseEntity.ok(WrongAnswerSummaryResponse.from(summaryService.find(sessionCode)));
    }
}
