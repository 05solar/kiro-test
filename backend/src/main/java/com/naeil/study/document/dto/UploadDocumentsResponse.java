package com.naeil.study.document.dto;

import com.naeil.study.document.entity.Document;
import java.util.List;

/** {@code POST /api/sessions/{sessionCode}/documents} 응답. 이번 요청으로 저장된 파일만 담는다. */
public record UploadDocumentsResponse(List<DocumentResponse> documents) {

    public static UploadDocumentsResponse from(List<Document> documents) {
        return new UploadDocumentsResponse(documents.stream().map(DocumentResponse::from).toList());
    }
}
