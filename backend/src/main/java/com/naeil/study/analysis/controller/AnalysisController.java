package com.naeil.study.analysis.controller;

import com.naeil.study.analysis.dto.AnalysisProgressResponse;
import com.naeil.study.analysis.dto.AnalysisResponse;
import com.naeil.study.analysis.progress.AnalysisProgressTracker;
import com.naeil.study.analysis.service.AnalysisService;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.topic.entity.Topic;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
 * <p>분석 요청은 동기라 응답이 올 때까지 화면이 기다린다. 그 동안 무엇이 진행 중인지는
 * {@code GET .../analysis/progress} 를 폴링해서 실제 조각 처리 수로 보여준다.
 */
@RestController
@RequestMapping("/api/sessions/{sessionCode}/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final AnalysisProgressTracker progressTracker;
    private final SessionService sessionService;

    public AnalysisController(
            AnalysisService analysisService,
            AnalysisProgressTracker progressTracker,
            SessionService sessionService
    ) {
        this.analysisService = analysisService;
        this.progressTracker = progressTracker;
        this.sessionService = sessionService;
    }

    /** 강의자료를 분석해 Topic을 만든다. 기존 Topic이 있으면 교체한다. */
    @PostMapping
    public ResponseEntity<AnalysisResponse> analyze(@PathVariable String sessionCode) {
        List<Topic> topics = analysisService.analyze(sessionCode);
        return ResponseEntity.ok(AnalysisResponse.of(sessionCode, topics.size()));
    }

    /**
     * 진행 중인 분석의 실제 진행도. 분석 요청과 병행해서 폴링한다.
     *
     * <p>분석을 시작한 적 없으면 {@code NONE} 이다. 404 가 아니다 —
     * "아직 시작 전"은 폴링 흐름에서 정상 상태이기 때문이다.
     */
    @GetMapping("/progress")
    public ResponseEntity<AnalysisProgressResponse> progress(@PathVariable String sessionCode) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        return ResponseEntity.ok(
                AnalysisProgressResponse.from(progressTracker.get(session.getId())));
    }
}
