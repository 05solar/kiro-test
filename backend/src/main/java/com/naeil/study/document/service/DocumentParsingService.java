package com.naeil.study.document.service;

import com.naeil.study.common.exception.BusinessException;
import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentPolicy;
import com.naeil.study.document.exception.DocumentParseFailedException;
import com.naeil.study.document.exception.NoExtractableTextException;
import com.naeil.study.document.parser.DocumentParser;
import com.naeil.study.document.parser.DocumentParserFactory;
import com.naeil.study.document.parser.ParsedDocument;
import com.naeil.study.document.parser.TextNormalizer;
import com.naeil.study.document.service.DocumentParseStateWriter.ParseTarget;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.storage.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 강의자료에서 텍스트를 추출해 저장한다.
 *
 * <pre>
 * UPLOADED → PARSING → Storage 읽기 → Parser 실행 → 정규화 → 검증 → PARSED
 *                                                                 ↘ PARSE_FAILED
 * </pre>
 *
 * <p><b>이 클래스에는 {@code @Transactional} 이 없다.</b> 파일 읽기와 텍스트 추출은
 * 문서 크기에 따라 오래 걸릴 수 있어서, 그 시간 동안 DB 커넥션을 잡고 있으면 안 된다.
 * 상태 변경은 {@link DocumentParseStateWriter} 의 짧은 트랜잭션으로 나눠 처리한다.
 *
 * <p>AI 호출은 하지 않는다. 이 단계의 책임은 파일에서 평문 텍스트를 뽑는 데까지다.
 */
@Service
public class DocumentParsingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParsingService.class);

    private final SessionService sessionService;
    private final DocumentParseStateWriter stateWriter;
    private final DocumentParserFactory parserFactory;
    private final TextNormalizer textNormalizer;
    private final StorageService storageService;

    public DocumentParsingService(
            SessionService sessionService,
            DocumentParseStateWriter stateWriter,
            DocumentParserFactory parserFactory,
            TextNormalizer textNormalizer,
            StorageService storageService
    ) {
        this.sessionService = sessionService;
        this.stateWriter = stateWriter;
        this.parserFactory = parserFactory;
        this.textNormalizer = textNormalizer;
        this.storageService = storageService;
    }

    /**
     * 문서 한 건을 파싱한다.
     *
     * <p>이미 {@code PARSED} 인 문서는 파일을 다시 읽지 않고 기존 결과를 그대로 돌려준다.
     * {@code PARSE_FAILED} 인 문서는 다시 시도한다.
     *
     * @throws com.naeil.study.document.exception.DocumentNotFoundException       해당 세션에 문서가 없음
     * @throws com.naeil.study.document.exception.DocumentAlreadyParsingException 이미 파싱 중
     * @throws DocumentParseFailedException                                      추출 실패
     * @throws NoExtractableTextException                                        쓸 만한 텍스트 없음
     */
    public Document parse(String sessionCode, UUID documentId) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        return parseDocument(session.getId(), documentId);
    }

    /**
     * 세션에 업로드된 문서를 한 번에 파싱한다.
     *
     * <p>대상은 아직 파싱하지 않은({@code UPLOADED}) 문서뿐이다. 이미 파싱된 문서는 건너뛴다.
     * 한 문서가 실패해도 나머지는 계속 처리하고, 결과는 문서별 상태로 확인한다.
     *
     * @return 세션의 전체 문서 (건너뛴 것 포함)
     */
    public List<Document> parseAll(String sessionCode) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        UUID sessionId = session.getId();

        List<UUID> targets = stateWriter.findParsableDocumentIds(sessionId);
        for (UUID documentId : targets) {
            try {
                parseDocument(sessionId, documentId);
            } catch (BusinessException e) {
                // 한 문서의 실패로 나머지를 멈추지 않는다. 상태는 이미 PARSE_FAILED 로 기록되었다.
                log.info("skip failed document during bulk parsing: sessionId={}, documentId={}, code={}",
                        sessionId, documentId, e.getErrorCode().name());
            }
        }
        return stateWriter.findAllDocuments(sessionId);
    }

    private Document parseDocument(UUID sessionId, UUID documentId) {
        Optional<ParseTarget> target = stateWriter.beginParsing(sessionId, documentId);
        if (target.isEmpty()) {
            // 이미 파싱된 문서. 결과를 그대로 돌려준다.
            return stateWriter.findOwnedDocument(sessionId, documentId);
        }

        ParseTarget parseTarget = target.get();
        try {
            String text = extractText(parseTarget);
            Document parsed = stateWriter.completeParsing(sessionId, documentId, text);
            log.info("document parsed: sessionId={}, documentId={}, fileType={}, characterCount={}",
                    sessionId, documentId, parseTarget.fileType(), parsed.getCharacterCount());
            return parsed;
        } catch (BusinessException e) {
            String reason = resolveReason(e);
            stateWriter.failParsing(sessionId, documentId, reason);
            log.warn("document parsing failed: sessionId={}, documentId={}, fileType={}, fileName={}, reason={}",
                    sessionId, documentId, parseTarget.fileType(), parseTarget.originalFileName(), reason);
            throw e;
        }
    }

    /**
     * Storage에서 파일을 읽어 텍스트를 뽑고 다듬는다. 트랜잭션 밖에서 실행된다.
     *
     * <p>추출한 텍스트 전체는 로그로 남기지 않는다.
     */
    private String extractText(ParseTarget target) {
        DocumentParser parser = parserFactory.getParser(target.fileType());
        ParsedDocument parsed;
        try (InputStream inputStream = storageService.load(target.storagePath())) {
            parsed = parser.parse(inputStream);
        } catch (IOException e) {
            throw new DocumentParseFailedException("failed to read stored file");
        }

        String normalized = textNormalizer.normalize(parsed.text());
        if (normalized.length() < DocumentPolicy.MIN_EXTRACTED_TEXT_LENGTH) {
            // 스캔본처럼 텍스트 레이어가 없는 파일. OCR은 하지 않는다.
            throw new NoExtractableTextException();
        }
        return normalized;
    }

    private String resolveReason(BusinessException e) {
        if (e instanceof DocumentParseFailedException failed) {
            return failed.getReason();
        }
        return e.getErrorCode().name();
    }
}
