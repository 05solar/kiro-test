package com.naeil.study.document.dto;

import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 파싱 결과 응답.
 *
 * <p>추출한 텍스트 전체는 담지 않는다. 문서 하나가 수만 자에 이를 수 있어 응답이 지나치게 커진다.
 * 텍스트가 필요한 곳은 AI 분석 서비스이고, 그쪽은 API가 아니라 리포지터리로 읽는다.
 */
public record DocumentParseResponse(
        UUID documentId,
        String originalFileName,
        DocumentStatus status,
        Integer characterCount,
        LocalDateTime parsedAt
) {

    public static DocumentParseResponse from(Document document) {
        return new DocumentParseResponse(
                document.getId(),
                document.getOriginalFileName(),
                document.getStatus(),
                document.getCharacterCount(),
                document.getParsedAt()
        );
    }
}
