package com.naeil.study.document.dto;

import com.naeil.study.document.entity.Document;
import java.util.List;

/** {@code GET /api/sessions/{sessionCode}/documents} 응답. 세션에 저장된 전체 파일을 담는다. */
public record DocumentListResponse(List<DocumentResponse> documents) {

    public static DocumentListResponse from(List<Document> documents) {
        return new DocumentListResponse(documents.stream().map(DocumentResponse::from).toList());
    }
}
