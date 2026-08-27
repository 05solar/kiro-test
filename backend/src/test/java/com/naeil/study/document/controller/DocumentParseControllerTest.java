package com.naeil.study.document.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.entity.DocumentStatus;
import com.naeil.study.document.exception.DocumentAlreadyParsingException;
import com.naeil.study.document.exception.DocumentNotFoundException;
import com.naeil.study.document.exception.DocumentParseFailedException;
import com.naeil.study.document.exception.NoExtractableTextException;
import com.naeil.study.document.service.DocumentParsingService;
import com.naeil.study.document.service.DocumentService;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.storage.StoredFile;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentController.class)
@DisplayName("DocumentController - 문서 파싱 API")
class DocumentParseControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 17, 20, 0);
    private static final String SESSION_CODE = "7K2M9QXF";
    private static final UUID DOCUMENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String EXTRACTED = "운영체제에서 프로세스는 실행 중인 프로그램을 의미한다.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private DocumentParsingService documentParsingService;

    private Document document(String fileName, DocumentStatus status) throws Exception {
        StudySession session = StudySession.create(SESSION_CODE, NOW, 30L);
        Document document = Document.create(session, fileName,
                new StoredFile("stored.pdf", "sessions/x/documents/stored.pdf"), DocumentFileType.PDF, 100, NOW);
        if (status == DocumentStatus.PARSED) {
            document.markParsed(EXTRACTED, NOW);
        } else if (status == DocumentStatus.PARSE_FAILED) {
            document.markParseFailed("pdf text extraction failed", NOW);
        }
        Field field = Document.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(document, DOCUMENT_ID);
        return document;
    }

    @Test
    @DisplayName("POST /documents/{id}/parse 정상 → 200, 파싱 결과를 반환한다")
    void parseReturns200() throws Exception {
        given(documentParsingService.parse(SESSION_CODE, DOCUMENT_ID))
                .willReturn(document("운영체제_1주차.pdf", DocumentStatus.PARSED));

        mockMvc.perform(post("/api/sessions/{sessionCode}/documents/{documentId}/parse",
                        SESSION_CODE, DOCUMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(DOCUMENT_ID.toString()))
                .andExpect(jsonPath("$.originalFileName").value("운영체제_1주차.pdf"))
                .andExpect(jsonPath("$.status").value("PARSED"))
                .andExpect(jsonPath("$.characterCount").value(EXTRACTED.length()))
                .andExpect(jsonPath("$.parsedAt").value("2026-08-27T17:20:00"))
                // 추출한 텍스트 전체는 응답에 넣지 않는다.
                .andExpect(jsonPath("$.text").doesNotExist())
                .andExpect(jsonPath("$.extractedText").doesNotExist());
    }

    @Test
    @DisplayName("POST parse 이미 파싱 중 → 409 DOCUMENT_ALREADY_PARSING")
    void parseReturns409WhenAlreadyParsing() throws Exception {
        willThrow(new DocumentAlreadyParsingException())
                .given(documentParsingService).parse(anyString(), any(UUID.class));

        mockMvc.perform(post("/api/sessions/{sessionCode}/documents/{documentId}/parse",
                        SESSION_CODE, DOCUMENT_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_ALREADY_PARSING"));
    }

    @Test
    @DisplayName("POST parse 추출 실패 → 422 DOCUMENT_PARSE_FAILED")
    void parseReturns422WhenParseFails() throws Exception {
        willThrow(new DocumentParseFailedException("pdf text extraction failed"))
                .given(documentParsingService).parse(anyString(), any(UUID.class));

        mockMvc.perform(post("/api/sessions/{sessionCode}/documents/{documentId}/parse",
                        SESSION_CODE, DOCUMENT_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOCUMENT_PARSE_FAILED"))
                .andExpect(jsonPath("$.message").value("문서 내용을 읽는 중 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("POST parse 텍스트 없는 PDF → 422 NO_EXTRACTABLE_TEXT")
    void parseReturns422WhenNoExtractableText() throws Exception {
        willThrow(new NoExtractableTextException())
                .given(documentParsingService).parse(anyString(), any(UUID.class));

        mockMvc.perform(post("/api/sessions/{sessionCode}/documents/{documentId}/parse",
                        SESSION_CODE, DOCUMENT_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_EXTRACTABLE_TEXT"))
                .andExpect(jsonPath("$.message").value("문서에서 학습에 사용할 텍스트를 추출할 수 없습니다."));
    }

    @Test
    @DisplayName("POST parse 다른 세션의 문서 → 404 (403이 아니다)")
    void parseReturns404ForOtherSessionDocument() throws Exception {
        willThrow(new DocumentNotFoundException())
                .given(documentParsingService).parse(anyString(), any(UUID.class));

        mockMvc.perform(post("/api/sessions/{sessionCode}/documents/{documentId}/parse",
                        SESSION_CODE, DOCUMENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST parse 존재하지 않는 세션 → 404 SESSION_NOT_FOUND")
    void parseReturns404WhenSessionNotFound() throws Exception {
        willThrow(new SessionNotFoundException())
                .given(documentParsingService).parse(anyString(), any(UUID.class));

        mockMvc.perform(post("/api/sessions/{sessionCode}/documents/{documentId}/parse",
                        "ZZZZZZZZ", DOCUMENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST parse documentId가 UUID 형식이 아니면 → 400")
    void parseReturns400ForMalformedId() throws Exception {
        mockMvc.perform(post("/api/sessions/{sessionCode}/documents/{documentId}/parse",
                        SESSION_CODE, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POST /documents/parse 전체 파싱 → 200, 세션 전체 문서 상태를 반환한다")
    void parseAllReturns200() throws Exception {
        given(documentParsingService.parseAll(SESSION_CODE)).willReturn(List.of(
                document("1주차.pdf", DocumentStatus.PARSED),
                document("2주차.pdf", DocumentStatus.PARSE_FAILED),
                document("3주차.pdf", DocumentStatus.UPLOADED)));

        mockMvc.perform(post("/api/sessions/{sessionCode}/documents/parse", SESSION_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(3))
                .andExpect(jsonPath("$.documents[0].status").value("PARSED"))
                .andExpect(jsonPath("$.documents[0].characterCount").value(EXTRACTED.length()))
                .andExpect(jsonPath("$.documents[1].status").value("PARSE_FAILED"))
                .andExpect(jsonPath("$.documents[1].characterCount").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.documents[2].status").value("UPLOADED"));
    }

    @Test
    @DisplayName("POST /documents/parse 존재하지 않는 세션 → 404")
    void parseAllReturns404() throws Exception {
        willThrow(new SessionNotFoundException()).given(documentParsingService).parseAll(anyString());

        mockMvc.perform(post("/api/sessions/{sessionCode}/documents/parse", "ZZZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }
}
