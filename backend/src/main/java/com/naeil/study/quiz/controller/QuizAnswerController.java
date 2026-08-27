package com.naeil.study.quiz.controller;

import com.naeil.study.quiz.dto.AnswerQuizRequest;
import com.naeil.study.quiz.dto.QuizAnswerResponse;
import com.naeil.study.quiz.service.QuizAnswerService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 답안 제출 API.
 *
 * <p>이미 답한 문제에 다시 제출하면 기존 결과를 그대로 돌려준다.
 * 새로 채점한 경우와 응답 형식이 같고, 상태 코드도 200이다.
 */
@RestController
@RequestMapping("/api/sessions/{sessionCode}/quizzes")
public class QuizAnswerController {

    private final QuizAnswerService quizAnswerService;

    public QuizAnswerController(QuizAnswerService quizAnswerService) {
        this.quizAnswerService = quizAnswerService;
    }

    /** 답안을 제출하고 채점 결과(정답·해설 포함)를 받는다. */
    @PostMapping("/{quizId}/answer")
    public ResponseEntity<QuizAnswerResponse> answer(
            @PathVariable String sessionCode,
            @PathVariable UUID quizId,
            @Valid @RequestBody AnswerQuizRequest request
    ) {
        return ResponseEntity.ok(QuizAnswerResponse.from(
                quizAnswerService.answer(sessionCode, quizId, request.selectedIndex())));
    }
}
