package com.naeil.study.studycontext.controller;

import com.naeil.study.studycontext.dto.StudyContextResponse;
import com.naeil.study.studycontext.dto.UpdateStudyContextRequest;
import com.naeil.study.studycontext.entity.StudyContext;
import com.naeil.study.studycontext.service.StudyContextService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학습 맥락 입력 / 조회 API.
 *
 * <p>삭제 API는 만들지 않는다. 네 항목을 모두 비운 채로 PUT하면 같은 효과가 난다.
 */
@RestController
@RequestMapping("/api/sessions/{sessionCode}/study-context")
public class StudyContextController {

    private final StudyContextService studyContextService;

    public StudyContextController(StudyContextService studyContextService) {
        this.studyContextService = studyContextService;
    }

    /** 학습 맥락을 저장하거나 통째로 교체한다. 없으면 만들고 있으면 고친다. */
    @PutMapping
    public ResponseEntity<StudyContextResponse> upsert(
            @PathVariable String sessionCode,
            @Valid @RequestBody UpdateStudyContextRequest request
    ) {
        StudyContext studyContext = studyContextService.upsert(sessionCode, request);
        return ResponseEntity.ok(StudyContextResponse.from(sessionCode, studyContext));
    }

    /**
     * 학습 맥락을 조회한다.
     *
     * <p>아직 입력하지 않았어도 404가 아니라 200에 전부 {@code null}로 응답한다.
     * 선택 입력이라 "없음"이 정상 상태이기 때문이다.
     */
    @GetMapping
    public ResponseEntity<StudyContextResponse> find(@PathVariable String sessionCode) {
        return ResponseEntity.ok(studyContextService.find(sessionCode)
                .map(studyContext -> StudyContextResponse.from(sessionCode, studyContext))
                .orElseGet(() -> StudyContextResponse.empty(sessionCode)));
    }
}
