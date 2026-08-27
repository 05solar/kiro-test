package com.naeil.study.curriculum.controller;

import com.naeil.study.curriculum.dto.CurriculumResponse;
import com.naeil.study.curriculum.service.CurriculumService;
import com.naeil.study.curriculum.service.CurriculumService.CurriculumResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학습 계획 생성 / 조회 API.
 *
 * <p>Request Body가 없다. 남은 학습 시간과 주제는 이미 DB에 있다.
 */
@RestController
@RequestMapping("/api/sessions/{sessionCode}/curriculum")
public class CurriculumController {

    private final CurriculumService curriculumService;

    public CurriculumController(CurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    /**
     * 최초 학습 계획을 만든다.
     *
     * <p>이미 계획이 있으면 만들지 않고 기존 것을 돌려준다.
     * 그래서 새로 만든 경우에만 201이고, 기존 계획을 돌려줄 때는 200이다.
     * 만들지 않았는데 201을 돌려주면 클라이언트가 생성 여부를 알 수 없다.
     */
    @PostMapping
    public ResponseEntity<CurriculumResponse> create(@PathVariable String sessionCode) {
        CurriculumResult result = curriculumService.create(sessionCode);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(CurriculumResponse.of(result.curriculum(), result.steps()));
    }

    /** 학습 계획을 조회한다. 아직 만들지 않았으면 404다. */
    @GetMapping
    public ResponseEntity<CurriculumResponse> find(@PathVariable String sessionCode) {
        CurriculumResult result = curriculumService.find(sessionCode);
        return ResponseEntity.ok(CurriculumResponse.of(result.curriculum(), result.steps()));
    }
}
