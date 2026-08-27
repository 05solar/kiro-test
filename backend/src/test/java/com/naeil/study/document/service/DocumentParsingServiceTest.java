package com.naeil.study.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.entity.DocumentStatus;
import com.naeil.study.document.exception.DocumentAlreadyParsingException;
import com.naeil.study.document.exception.DocumentNotFoundException;
import com.naeil.study.document.exception.DocumentParseFailedException;
import com.naeil.study.document.exception.NoExtractableTextException;
import com.naeil.study.document.parser.DocumentParser;
import com.naeil.study.document.parser.DocumentParserFactory;
import com.naeil.study.document.parser.ParsedDocument;
import com.naeil.study.document.parser.TextNormalizer;
import com.naeil.study.document.service.DocumentParseStateWriter.ParseTarget;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.storage.StorageService;
import com.naeil.study.storage.StoredFile;
import com.naeil.study.storage.exception.StoredFileNotFoundException;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentParsingService - 문서 파싱")
class DocumentParsingServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 17, 20, 0);
    private static final UUID SESSION_ID = UUID.fromString("6f79a1b2-0000-4000-8000-000000000001");
    private static final UUID DOCUMENT_ID = UUID.fromString("11111111-0000-4000-8000-000000000001");
    private static final String SESSION_CODE = "7K2M9QXF";
    private static final String STORAGE_PATH = "sessions/x/documents/a.pdf";
    private static final String EXTRACTED = "운영체제에서 프로세스는 실행 중인 프로그램을 의미한다.";

    @Mock
    private SessionService sessionService;

    @Mock
    private DocumentParseStateWriter stateWriter;

    @Mock
    private DocumentParserFactory parserFactory;

    @Mock
    private StorageService storageService;

    @Mock
    private DocumentParser parser;

    private DocumentParsingService parsingService;
    private StudySession session;

    @BeforeEach
    void setUp() throws Exception {
        parsingService = new DocumentParsingService(
                sessionService, stateWriter, parserFactory, new TextNormalizer(), storageService);
        session = StudySession.create(SESSION_CODE, NOW.minusHours(1), 30L);
        Field field = StudySession.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(session, SESSION_ID);
    }

    private Document document(DocumentStatus status, String text) {
        Document document = Document.create(session, "운영체제_1주차.pdf",
                new StoredFile("a.pdf", STORAGE_PATH), DocumentFileType.PDF, 100, NOW);
        if (status == DocumentStatus.PARSED) {
            document.markParsed(text, NOW);
        } else if (status == DocumentStatus.PARSE_FAILED) {
            document.markParseFailed("previous failure", NOW);
        }
        return document;
    }

    private void givenSessionFound() {
        given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
    }

    private void givenParseTarget() {
        given(stateWriter.beginParsing(SESSION_ID, DOCUMENT_ID)).willReturn(Optional.of(
                new ParseTarget(DOCUMENT_ID, DocumentFileType.PDF, STORAGE_PATH, "운영체제_1주차.pdf")));
    }

    private void givenStorageReturnsFile() {
        given(storageService.load(STORAGE_PATH))
                .willReturn(new ByteArrayInputStream("pdf bytes".getBytes(StandardCharsets.UTF_8)));
        given(parserFactory.getParser(DocumentFileType.PDF)).willReturn(parser);
    }

    @Nested
    @DisplayName("개별 파싱")
    class ParseOne {

        @Test
        @DisplayName("UPLOADED 문서를 파싱하면 텍스트가 저장되고 PARSED가 된다")
        void parsesUploadedDocument() {
            givenSessionFound();
            givenParseTarget();
            givenStorageReturnsFile();
            given(parser.parse(any())).willReturn(ParsedDocument.of(EXTRACTED));
            Document parsed = document(DocumentStatus.PARSED, EXTRACTED);
            given(stateWriter.completeParsing(SESSION_ID, DOCUMENT_ID, EXTRACTED)).willReturn(parsed);

            Document result = parsingService.parse(SESSION_CODE, DOCUMENT_ID);

            assertThat(result.getStatus()).isEqualTo(DocumentStatus.PARSED);
            assertThat(result.getExtractedText()).isEqualTo(EXTRACTED);
            assertThat(result.getCharacterCount()).isEqualTo(EXTRACTED.length());
            assertThat(result.getParsedAt()).isEqualTo(NOW);
            verify(stateWriter).completeParsing(SESSION_ID, DOCUMENT_ID, EXTRACTED);
        }

        @Test
        @DisplayName("추출한 텍스트를 정규화해서 저장한다")
        void normalizesExtractedText() {
            givenSessionFound();
            givenParseTarget();
            givenStorageReturnsFile();
            given(parser.parse(any()))
                    .willReturn(ParsedDocument.of("1장 프로세스   \r\n\r\n\r\n\r\n1.1 프로세스란\r\n실행 중인 프로그램이다.  "));
            given(stateWriter.completeParsing(eq(SESSION_ID), eq(DOCUMENT_ID), anyString()))
                    .willReturn(document(DocumentStatus.PARSED, EXTRACTED));

            parsingService.parse(SESSION_CODE, DOCUMENT_ID);

            verify(stateWriter).completeParsing(SESSION_ID, DOCUMENT_ID,
                    "1장 프로세스\n\n1.1 프로세스란\n실행 중인 프로그램이다.");
        }

        @Test
        @DisplayName("이미 PARSED인 문서는 파일을 다시 읽지 않고 기존 결과를 돌려준다")
        void returnsExistingResultWhenAlreadyParsed() {
            givenSessionFound();
            given(stateWriter.beginParsing(SESSION_ID, DOCUMENT_ID)).willReturn(Optional.empty());
            Document parsed = document(DocumentStatus.PARSED, EXTRACTED);
            given(stateWriter.findOwnedDocument(SESSION_ID, DOCUMENT_ID)).willReturn(parsed);

            Document result = parsingService.parse(SESSION_CODE, DOCUMENT_ID);

            assertThat(result.getStatus()).isEqualTo(DocumentStatus.PARSED);
            verify(storageService, never()).load(anyString());
            verify(stateWriter, never()).completeParsing(any(), any(), anyString());
        }

        @Test
        @DisplayName("이미 PARSING 중이면 409가 발생한다")
        void throwsWhenAlreadyParsing() {
            givenSessionFound();
            willThrow(new DocumentAlreadyParsingException())
                    .given(stateWriter).beginParsing(SESSION_ID, DOCUMENT_ID);

            assertThatThrownBy(() -> parsingService.parse(SESSION_CODE, DOCUMENT_ID))
                    .isInstanceOf(DocumentAlreadyParsingException.class);

            verify(storageService, never()).load(anyString());
        }

        @Test
        @DisplayName("Storage에 파일이 없으면 PARSE_FAILED로 기록한다")
        void marksFailedWhenStoredFileMissing() {
            givenSessionFound();
            givenParseTarget();
            given(parserFactory.getParser(DocumentFileType.PDF)).willReturn(parser);
            willThrow(new StoredFileNotFoundException()).given(storageService).load(STORAGE_PATH);

            assertThatThrownBy(() -> parsingService.parse(SESSION_CODE, DOCUMENT_ID))
                    .isInstanceOf(StoredFileNotFoundException.class);

            verify(stateWriter).failParsing(SESSION_ID, DOCUMENT_ID, "STORED_FILE_NOT_FOUND");
            verify(stateWriter, never()).completeParsing(any(), any(), anyString());
        }

        @Test
        @DisplayName("Parser가 실패하면 PARSE_FAILED로 기록하고 사유를 남긴다")
        void marksFailedWhenParserThrows() {
            givenSessionFound();
            givenParseTarget();
            givenStorageReturnsFile();
            given(parser.parse(any())).willThrow(new DocumentParseFailedException("pdf text extraction failed"));

            assertThatThrownBy(() -> parsingService.parse(SESSION_CODE, DOCUMENT_ID))
                    .isInstanceOf(DocumentParseFailedException.class);

            verify(stateWriter).failParsing(SESSION_ID, DOCUMENT_ID, "pdf text extraction failed");
        }

        @Test
        @DisplayName("추출 텍스트가 너무 짧으면 NO_EXTRACTABLE_TEXT로 실패한다 (스캔 PDF)")
        void marksFailedWhenNoExtractableText() {
            givenSessionFound();
            givenParseTarget();
            givenStorageReturnsFile();
            given(parser.parse(any())).willReturn(ParsedDocument.of("   \n\n  ."));

            assertThatThrownBy(() -> parsingService.parse(SESSION_CODE, DOCUMENT_ID))
                    .isInstanceOf(NoExtractableTextException.class);

            verify(stateWriter).failParsing(SESSION_ID, DOCUMENT_ID, "NO_EXTRACTABLE_TEXT");
        }

        @Test
        @DisplayName("PARSE_FAILED 문서를 다시 요청하면 재시도한다")
        void retriesFailedDocument() {
            givenSessionFound();
            givenParseTarget();
            givenStorageReturnsFile();
            given(parser.parse(any())).willReturn(ParsedDocument.of(EXTRACTED));
            given(stateWriter.completeParsing(SESSION_ID, DOCUMENT_ID, EXTRACTED))
                    .willReturn(document(DocumentStatus.PARSED, EXTRACTED));

            Document result = parsingService.parse(SESSION_CODE, DOCUMENT_ID);

            assertThat(result.getStatus()).isEqualTo(DocumentStatus.PARSED);
        }

        @Test
        @DisplayName("다른 세션의 문서는 404가 발생한다")
        void throwsWhenDocumentBelongsToAnotherSession() {
            givenSessionFound();
            willThrow(new DocumentNotFoundException())
                    .given(stateWriter).beginParsing(SESSION_ID, DOCUMENT_ID);

            assertThatThrownBy(() -> parsingService.parse(SESSION_CODE, DOCUMENT_ID))
                    .isInstanceOf(DocumentNotFoundException.class);

            verify(storageService, never()).load(anyString());
        }

        @Test
        @DisplayName("존재하지 않는 세션이면 404가 발생한다")
        void throwsWhenSessionNotFound() {
            given(sessionService.getSessionAndTouch("ZZZZZZZZ")).willThrow(new SessionNotFoundException());

            assertThatThrownBy(() -> parsingService.parse("ZZZZZZZZ", DOCUMENT_ID))
                    .isInstanceOf(SessionNotFoundException.class);
        }

        @Test
        @DisplayName("파싱 요청은 세션 접근으로 기록된다")
        void refreshesSessionAccessTime() {
            givenSessionFound();
            given(stateWriter.beginParsing(SESSION_ID, DOCUMENT_ID)).willReturn(Optional.empty());
            given(stateWriter.findOwnedDocument(SESSION_ID, DOCUMENT_ID))
                    .willReturn(document(DocumentStatus.PARSED, EXTRACTED));

            parsingService.parse(SESSION_CODE, DOCUMENT_ID);

            verify(sessionService).getSessionAndTouch(SESSION_CODE);
        }
    }

    @Nested
    @DisplayName("전체 파싱")
    class ParseAll {

        private final UUID secondId = UUID.fromString("11111111-0000-4000-8000-000000000002");

        @Test
        @DisplayName("아직 파싱하지 않은 문서만 처리한다")
        void parsesOnlyUploadedDocuments() {
            givenSessionFound();
            given(stateWriter.findParsableDocumentIds(SESSION_ID)).willReturn(List.of(DOCUMENT_ID, secondId));
            given(stateWriter.beginParsing(eq(SESSION_ID), any(UUID.class))).willAnswer(invocation ->
                    Optional.of(new ParseTarget(invocation.getArgument(1), DocumentFileType.PDF, STORAGE_PATH, "a.pdf")));
            given(parserFactory.getParser(DocumentFileType.PDF)).willReturn(parser);
            given(storageService.load(STORAGE_PATH))
                    .willAnswer(i -> new ByteArrayInputStream("pdf".getBytes(StandardCharsets.UTF_8)));
            given(parser.parse(any())).willReturn(ParsedDocument.of(EXTRACTED));
            given(stateWriter.completeParsing(eq(SESSION_ID), any(UUID.class), anyString()))
                    .willReturn(document(DocumentStatus.PARSED, EXTRACTED));
            given(stateWriter.findAllDocuments(SESSION_ID))
                    .willReturn(List.of(document(DocumentStatus.PARSED, EXTRACTED)));

            parsingService.parseAll(SESSION_CODE);

            verify(stateWriter).beginParsing(SESSION_ID, DOCUMENT_ID);
            verify(stateWriter).beginParsing(SESSION_ID, secondId);
            verify(stateWriter).completeParsing(SESSION_ID, DOCUMENT_ID, EXTRACTED);
            verify(stateWriter).completeParsing(SESSION_ID, secondId, EXTRACTED);
        }

        @Test
        @DisplayName("파싱할 문서가 없으면 아무것도 하지 않고 현재 목록을 돌려준다")
        void doesNothingWhenNoTargets() {
            givenSessionFound();
            given(stateWriter.findParsableDocumentIds(SESSION_ID)).willReturn(List.of());
            Document alreadyParsed = document(DocumentStatus.PARSED, EXTRACTED);
            given(stateWriter.findAllDocuments(SESSION_ID)).willReturn(List.of(alreadyParsed));

            List<Document> documents = parsingService.parseAll(SESSION_CODE);

            assertThat(documents).containsExactly(alreadyParsed);
            verify(stateWriter, never()).beginParsing(any(), any());
        }

        @Test
        @DisplayName("한 문서가 실패해도 나머지는 계속 처리한다")
        void continuesWhenOneDocumentFails() {
            givenSessionFound();
            given(stateWriter.findParsableDocumentIds(SESSION_ID)).willReturn(List.of(DOCUMENT_ID, secondId));
            given(stateWriter.beginParsing(eq(SESSION_ID), any(UUID.class))).willAnswer(invocation ->
                    Optional.of(new ParseTarget(invocation.getArgument(1), DocumentFileType.PDF, STORAGE_PATH, "a.pdf")));
            given(parserFactory.getParser(DocumentFileType.PDF)).willReturn(parser);
            given(storageService.load(STORAGE_PATH))
                    .willAnswer(i -> new ByteArrayInputStream("pdf".getBytes(StandardCharsets.UTF_8)));
            given(parser.parse(any()))
                    .willThrow(new DocumentParseFailedException("pdf text extraction failed"))
                    .willReturn(ParsedDocument.of(EXTRACTED));
            given(stateWriter.completeParsing(eq(SESSION_ID), eq(secondId), anyString()))
                    .willReturn(document(DocumentStatus.PARSED, EXTRACTED));
            given(stateWriter.findAllDocuments(SESSION_ID)).willReturn(List.of());

            parsingService.parseAll(SESSION_CODE);

            verify(stateWriter).failParsing(SESSION_ID, DOCUMENT_ID, "pdf text extraction failed");
            verify(stateWriter).completeParsing(SESSION_ID, secondId, EXTRACTED);
        }
    }
}
