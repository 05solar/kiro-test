package com.naeil.study.document.dto;

import com.naeil.study.document.entity.Document;
import java.util.List;

/**
 * {@code POST /api/sessions/{sessionCode}/documents/parse} 응답.
 *
 * <p>이번 요청으로 파싱한 문서뿐 아니라 세션의 전체 문서를 담는다.
 * 건너뛴 문서와 실패한 문서를 함께 보여줘야 화면에서 지금 상태를 그대로 그릴 수 있다.
 */
public record ParseDocumentsResponse(List<DocumentParseResponse> documents) {

    public static ParseDocumentsResponse from(List<Document> documents) {
        return new ParseDocumentsResponse(documents.stream().map(DocumentParseResponse::from).toList());
    }
}
