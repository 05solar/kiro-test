package com.naeil.study.session.controller;

import com.naeil.study.session.dto.CreateSessionResponse;
import com.naeil.study.session.dto.ExamResponse;
import com.naeil.study.session.dto.SessionResponse;
import com.naeil.study.session.dto.UpdateExamRequest;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세션 생성 / 복구 API.
 *
 * <p>회원 개념이 없으므로 이 컨트롤러가 학습 공간에 접근하는 유일한 진입점이다.
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** 새 학습 세션을 생성하고 8자리 코드를 발급한다. */
    @PostMapping
    public ResponseEntity<CreateSessionResponse> createSession() {
        StudySession session = sessionService.createSession();
        return ResponseEntity.status(HttpStatus.CREATED).body(CreateSessionResponse.from(session));
    }

    /** 8자리 코드로 기존 학습 세션을 조회한다. 조회에 성공하면 보관 기한이 연장된다. */
    @GetMapping("/{sessionCode}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable String sessionCode) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        return ResponseEntity.ok(SessionResponse.from(session));
    }

    /**
     * 시험 정보를 등록하거나 수정한다.
     *
     * <p>같은 요청을 다시 보내면 덮어쓴다. 저장 시 시험까지 남은 실제 시간을 반영해
     * {@code remainingStudyMinutes}가 다시 계산된다.
     */
    @PutMapping("/{sessionCode}/exam")
    public ResponseEntity<ExamResponse> updateExamInfo(
            @PathVariable String sessionCode,
            @Valid @RequestBody UpdateExamRequest request
    ) {
        StudySession session = sessionService.updateExamInfo(sessionCode, request);
        return ResponseEntity.ok(ExamResponse.from(session));
    }
}
