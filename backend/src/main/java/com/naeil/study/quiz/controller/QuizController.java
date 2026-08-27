package com.naeil.study.quiz.controller;

import com.naeil.study.quiz.dto.QuizListResponse;
import com.naeil.study.quiz.dto.QuizResultsResponse;
import com.naeil.study.quiz.service.QuizAnswerService;
import com.naeil.study.quiz.service.QuizGenerationService;
import com.naeil.study.quiz.service.QuizGenerationService.QuizGenerationResult;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Topic 퀴즈 생성 / 조회 / 결과 집계 API.
 *
 * <p>생성 요청에 Request Body 가 없다. 출제 근거(강의자료, Topic, 학습 맥락)는 이미 DB에 있다.
 *
 * <p>문제 응답에는 정답 인덱스와 해설이 없다. 답안 제출 API 에서만 공개한다.
 */
@RestController
@RequestMapping("/api/sessions/{sessionCode}/topics/{topicId}")
public class QuizController {

    private final QuizGenerationService quizGenerationService;
    private final QuizAnswerService quizAnswerService;

    public QuizController(
            QuizGenerationService quizGenerationService,
            QuizAnswerService quizAnswerService
    ) {
        this.quizGenerationService = quizGenerationService;
        this.quizAnswerService = quizAnswerService;
    }

    /**
     * Topic 의 퀴즈를 생성한다.
     *
     * <p>이미 있으면 AI 를 부르지 않고 기존 것을 돌려준다.
     * 그래서 새로 만든 경우에만 201이고, 기존 퀴즈를 돌려줄 때는 200이다.
     */
    @PostMapping("/quizzes")
    public ResponseEntity<QuizListResponse> generate(
            @PathVariable String sessionCode,
            @PathVariable UUID topicId
    ) {
        QuizGenerationResult result = quizGenerationService.generate(sessionCode, topicId);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(QuizListResponse.of(result.topic(), result.quizzes()));
    }

    /** Topic 의 퀴즈를 조회한다. 아직 생성하지 않았으면 404다. */
    @GetMapping("/quizzes")
    public ResponseEntity<QuizListResponse> find(
            @PathVariable String sessionCode,
            @PathVariable UUID topicId
    ) {
        QuizGenerationResult result = quizGenerationService.find(sessionCode, topicId);
        return ResponseEntity.ok(QuizListResponse.of(result.topic(), result.quizzes()));
    }

    /** Topic 의 채점 결과를 집계한다. 아직 퀴즈가 없으면 404다. */
    @GetMapping("/quiz-results")
    public ResponseEntity<QuizResultsResponse> results(
            @PathVariable String sessionCode,
            @PathVariable UUID topicId
    ) {
        return ResponseEntity.ok(
                QuizResultsResponse.from(quizAnswerService.results(sessionCode, topicId)));
    }
}
