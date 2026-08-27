package com.naeil.study.analysis.controller;

import com.naeil.study.analysis.dto.AnalysisResponse;
import com.naeil.study.analysis.service.AnalysisService;
import com.naeil.study.topic.entity.Topic;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 분석 API.
 *
 * <p>Request Body가 없다. 분석에 필요한 강의자료와 학습 맥락은 이미 DB에 있으므로
 * 프론트가 텍스트를 다시 올려보내지 않는다.
 *
 * <p>분석 진행 상태를 위한 별도 API를 만들지 않는다.
 * {@code GET /api/sessions/{sessionCode}} 의 {@code status} 로 확인할 수 있다.
 */
@RestController
@RequestMapping("/api/sessions/{sessionCode}/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /** 강의자료를 분석해 Topic을 만든다. 기존 Topic이 있으면 교체한다. */
    @PostMapping
    public ResponseEntity<AnalysisResponse> analyze(@PathVariable String sessionCode) {
        List<Topic> topics = analysisService.analyze(sessionCode);
        return ResponseEntity.ok(AnalysisResponse.of(sessionCode, topics.size()));
    }
}
