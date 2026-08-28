package com.naeil.study.quiz.controller;

import com.naeil.study.quiz.dto.QuizListResponse;
import com.naeil.study.quiz.dto.QuizResultsResponse;
import com.naeil.study.quiz.dto.QuizReviewResponse;
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

    /**
     * 같은 학습 범위로 <b>새 회차</b>의 문제를 만든다.
     *
     * <p>기존 문제를 지우거나 고치지 않는다. 회차를 하나 올려 새로 쌓고, 이전 회차의
     * 문제 문장을 AI 에 함께 보내 중복을 피한다.
     *
     * <p>"오답 다시 풀기"와 다른 기능이다. 그쪽은 이미 낸 문제를 다시 보는 것이고,
     * 이쪽은 같은 범위에서 다른 문제를 만든다.
     *
     * <p>경로를 {@code /api/quizzes/regenerate} 대신 세션 아래에 둔 이유는
     * 다른 모든 엔드포인트와 같은 소유권 검사를 지나게 하기 위해서다. 이 서비스에는
     * 회원이 없고 8자리 코드가 유일한 접근 키라, 세션 밖의 경로는 소유자를 확인할 방법이 없다.
     */
    @PostMapping("/quizzes/regenerate")
    public ResponseEntity<QuizListResponse> regenerate(
            @PathVariable String sessionCode,
            @PathVariable UUID topicId
    ) {
        QuizGenerationResult result = quizGenerationService.regenerate(sessionCode, topicId);
        return ResponseEntity.status(HttpStatus.CREATED)
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

    /**
     * Topic 의 풀이 내역을 문제·정답·해설까지 함께 조회한다. 아직 퀴즈가 없으면 404다.
     *
     * <p>{@code /quiz-results} 와 나눠 둔 이유는 담는 것이 다르기 때문이다. 그쪽은 점수
     * 집계라 숫자만 있으면 되고, 이쪽은 "무엇을 틀렸는지" 화면이라 문제 문장이 필요하다.
     * 점수만 필요한 화면이 문제 본문까지 받아 갈 이유는 없다.
     */
    @GetMapping("/quiz-review")
    public ResponseEntity<QuizReviewResponse> review(
            @PathVariable String sessionCode,
            @PathVariable UUID topicId
    ) {
        return ResponseEntity.ok(
                QuizReviewResponse.from(quizAnswerService.review(sessionCode, topicId)));
    }
}
