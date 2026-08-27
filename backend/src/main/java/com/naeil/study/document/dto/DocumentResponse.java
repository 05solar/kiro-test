package com.naeil.study.document.dto;

import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.entity.DocumentStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 강의자료 한 건의 응답 형식.
 *
 * <p>{@code storedFileName}과 {@code storagePath}는 담지 않는다.
 * 사용자에게 필요 없고, 서버 저장 구조를 드러낼 이유도 없다.
 */
public record DocumentResponse(
        UUID id,
        String originalFileName,
        DocumentFileType fileType,
        long fileSize,
        DocumentStatus status,
        Integer characterCount,
        LocalDateTime parsedAt,
        LocalDateTime createdAt
) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getOriginalFileName(),
                document.getFileType(),
                document.getFileSize(),
                document.getStatus(),
                document.getCharacterCount(),
                document.getParsedAt(),
                document.getCreatedAt()
        );
    }
}
