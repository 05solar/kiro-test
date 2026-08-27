package com.naeil.study.document.controller;

import com.naeil.study.document.dto.DocumentListResponse;
import com.naeil.study.document.dto.DocumentParseResponse;
import com.naeil.study.document.dto.ParseDocumentsResponse;
import com.naeil.study.document.dto.UploadDocumentsResponse;
import com.naeil.study.document.entity.Document;
import com.naeil.study.document.service.DocumentParsingService;
import com.naeil.study.document.service.DocumentService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 강의자료 업로드 / 목록 / 삭제 API.
 *
 * <p>세션 코드가 곧 접근 권한이므로 모든 경로가 세션 아래에 놓인다.
 * 문서 ID만으로 접근하는 경로는 만들지 않는다.
 */
@RestController
@RequestMapping("/api/sessions/{sessionCode}/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentParsingService documentParsingService;

    public DocumentController(DocumentService documentService, DocumentParsingService documentParsingService) {
        this.documentService = documentService;
        this.documentParsingService = documentParsingService;
    }

    /** 강의자료를 업로드한다. 한 요청은 전체 성공하거나 전체 실패한다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadDocumentsResponse> upload(
            @PathVariable String sessionCode,
            @RequestPart("files") List<MultipartFile> files
    ) {
        List<Document> documents = documentService.upload(sessionCode, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(UploadDocumentsResponse.from(documents));
    }

    /** 세션에 업로드된 강의자료 목록을 조회한다. */
    @GetMapping
    public ResponseEntity<DocumentListResponse> findAll(@PathVariable String sessionCode) {
        List<Document> documents = documentService.findAll(sessionCode);
        return ResponseEntity.ok(DocumentListResponse.from(documents));
    }

    /**
     * 세션에 업로드된 강의자료를 한 번에 파싱한다.
     *
     * <p>아직 파싱하지 않은 문서만 처리한다. 이미 파싱된 문서는 건너뛴다.
     * 한 문서가 실패해도 나머지는 계속 처리하므로 응답은 항상 200이고,
     * 성공 여부는 문서별 {@code status} 로 확인한다.
     */
    @PostMapping("/parse")
    public ResponseEntity<ParseDocumentsResponse> parseAll(@PathVariable String sessionCode) {
        List<Document> documents = documentParsingService.parseAll(sessionCode);
        return ResponseEntity.ok(ParseDocumentsResponse.from(documents));
    }

    /**
     * 강의자료 한 건을 파싱한다.
     *
     * <p>이미 파싱된 문서에 다시 요청하면 파일을 다시 읽지 않고 기존 결과를 돌려준다.
     * 실패한 문서는 다시 요청해 재시도할 수 있다.
     */
    @PostMapping("/{documentId}/parse")
    public ResponseEntity<DocumentParseResponse> parse(
            @PathVariable String sessionCode,
            @PathVariable UUID documentId
    ) {
        Document document = documentParsingService.parse(sessionCode, documentId);
        return ResponseEntity.ok(DocumentParseResponse.from(document));
    }

    /** 강의자료를 삭제한다. 다른 세션의 문서는 조회되지 않으므로 404가 나간다. */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(
            @PathVariable String sessionCode,
            @PathVariable UUID documentId
    ) {
        documentService.delete(sessionCode, documentId);
        return ResponseEntity.noContent().build();
    }
}
