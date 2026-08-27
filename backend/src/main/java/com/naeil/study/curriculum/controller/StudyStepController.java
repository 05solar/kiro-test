package com.naeil.study.curriculum.controller;

import com.naeil.study.curriculum.dto.StepCompletionResponse;
import com.naeil.study.curriculum.dto.StudyStepProgressResponse;
import com.naeil.study.curriculum.service.StudyStepService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학습 단계 진행 API.
 *
 * <p>Request Body가 없다. 실제 학습시간은 서버가 {@code startedAt}과 현재 시각으로 계산한다.
 * 화면의 타이머 값을 받아 그대로 저장하면 조작할 수 있고, 여러 기기에서 접속했을 때
 * 어느 값을 믿어야 할지도 정할 수 없다.
 *
 * <p>상태 전이 규칙은 모두 {@link StudyStepService}와 엔티티에 있다. 여기서는 다루지 않는다.
 */
@RestController
@RequestMapping("/api/sessions/{sessionCode}/steps")
public class StudyStepController {

    private final StudyStepService studyStepService;

    public StudyStepController(StudyStepService studyStepService) {
        this.studyStepService = studyStepService;
    }

    /**
     * 학습을 시작한다.
     *
     * <p>이미 진행 중인 같은 단계에 다시 요청하면 기존 상태를 그대로 돌려준다.
     * 그래서 새로 시작한 경우와 응답이 같고, 상태 코드도 200이다.
     */
    @PostMapping("/{stepId}/start")
    public ResponseEntity<StudyStepProgressResponse> start(
            @PathVariable String sessionCode,
            @PathVariable UUID stepId
    ) {
        return ResponseEntity.ok(
                StudyStepProgressResponse.from(studyStepService.start(sessionCode, stepId)));
    }

    /** 학습을 완료하고 다음 단계를 함께 돌려준다. */
    @PostMapping("/{stepId}/complete")
    public ResponseEntity<StepCompletionResponse> complete(
            @PathVariable String sessionCode,
            @PathVariable UUID stepId
    ) {
        return ResponseEntity.ok(
                StepCompletionResponse.from(studyStepService.complete(sessionCode, stepId)));
    }
}
