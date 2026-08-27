package com.naeil.study.document.service;

import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.entity.DocumentStatus;
import com.naeil.study.document.exception.DocumentAlreadyParsingException;
import com.naeil.study.document.exception.DocumentNotFoundException;
import com.naeil.study.document.repository.DocumentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파싱 과정의 DB 상태 변경만 담당한다.
 *
 * <p>파일 읽기와 텍스트 추출은 시간이 걸린다. 그 시간 동안 DB 트랜잭션을 잡고 있지 않도록
 * 상태 변경을 짧은 트랜잭션 여러 개로 나눴다.
 *
 * <pre>
 * beginParsing()   tx  상태를 PARSING으로 바꾸고 커밋
 *      ↓
 * (트랜잭션 밖)        Storage 읽기 + 텍스트 추출 + 정규화
 *      ↓
 * completeParsing() tx  결과 저장
 * failParsing()     tx  실패 기록
 * </pre>
 *
 * <p>{@link DocumentParsingService}가 자기 자신의 메서드를 호출하면 프록시를 거치지 않아
 * 트랜잭션이 적용되지 않는다. 그래서 별도의 빈으로 분리했다.
 */
@Service
public class DocumentParseStateWriter {

    /**
     * 파싱에 필요한 정보만 담은 스냅샷. 트랜잭션 밖에서 엔티티를 들고 다니지 않기 위해 쓴다.
     */
    public record ParseTarget(
            UUID documentId,
            DocumentFileType fileType,
            String storagePath,
            String originalFileName
    ) {
    }

    private final DocumentRepository documentRepository;
    private final Clock clock;

    public DocumentParseStateWriter(DocumentRepository documentRepository, Clock clock) {
        this.documentRepository = documentRepository;
        this.clock = clock;
    }

    /**
     * 문서를 찾아 파싱 시작 상태로 바꾼다.
     *
     * @return 파싱 대상. 이미 {@code PARSED} 라서 다시 읽을 필요가 없으면 비어 있다
     * @throws DocumentNotFoundException       해당 세션에 그 문서가 없는 경우
     * @throws DocumentAlreadyParsingException 이미 파싱 중인 경우
     */
    @Transactional
    public Optional<ParseTarget> beginParsing(UUID sessionId, UUID documentId) {
        Document document = findOwned(sessionId, documentId);
        if (document.isParsing()) {
            throw new DocumentAlreadyParsingException();
        }
        if (document.isParsed()) {
            // 이미 결과가 있으면 파일을 다시 읽지 않는다.
            return Optional.empty();
        }
        document.startParsing(now());
        return Optional.of(new ParseTarget(
                document.getId(), document.getFileType(), document.getStoragePath(), document.getOriginalFileName()));
    }

    /** 추출 결과를 저장하고 상태를 {@code PARSED} 로 바꾼다. */
    @Transactional
    public Document completeParsing(UUID sessionId, UUID documentId, String text) {
        Document document = findOwned(sessionId, documentId);
        document.markParsed(text, now());
        return document;
    }

    /** 실패 원인을 기록하고 상태를 {@code PARSE_FAILED} 로 바꾼다. */
    @Transactional
    public Document failParsing(UUID sessionId, UUID documentId, String reason) {
        Document document = findOwned(sessionId, documentId);
        document.markParseFailed(reason, now());
        return document;
    }

    /** 세션 소유 문서를 조회한다. */
    @Transactional(readOnly = true)
    public Document findOwnedDocument(UUID sessionId, UUID documentId) {
        return findOwned(sessionId, documentId);
    }

    /** 아직 파싱하지 않은 문서 목록. 전체 파싱의 대상이다. */
    @Transactional(readOnly = true)
    public List<UUID> findParsableDocumentIds(UUID sessionId) {
        return documentRepository
                .findAllByStudySessionIdAndStatusOrderByCreatedAtAsc(sessionId, DocumentStatus.UPLOADED)
                .stream()
                .map(Document::getId)
                .toList();
    }

    /** 세션의 전체 문서를 업로드 순서대로 조회한다. */
    @Transactional(readOnly = true)
    public List<Document> findAllDocuments(UUID sessionId) {
        return documentRepository.findAllByStudySessionIdOrderByCreatedAtAsc(sessionId);
    }

    private Document findOwned(UUID sessionId, UUID documentId) {
        return documentRepository.findByIdAndStudySessionId(documentId, sessionId)
                .orElseThrow(DocumentNotFoundException::new);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
