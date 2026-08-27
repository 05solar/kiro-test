package com.naeil.study.document.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.exception.DocumentNotFoundException;
import com.naeil.study.document.exception.FileCountExceededException;
import com.naeil.study.document.exception.UnsupportedFileTypeException;
import com.naeil.study.document.service.DocumentParsingService;
import com.naeil.study.document.service.DocumentService;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.storage.StoredFile;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentController.class)
@DisplayName("DocumentController - 강의자료 API")
class DocumentControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 16, 30, 0);
    private static final String SESSION_CODE = "7K2M9QXF";
    private static final UUID DOCUMENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private DocumentParsingService documentParsingService;

    private MockMultipartFile filePart(String fileName, String contentType) {
        return new MockMultipartFile("files", fileName, contentType, "content".getBytes(StandardCharsets.UTF_8));
    }

    private Document document(String originalFileName, DocumentFileType type, long size) throws Exception {
        StudySession session = StudySession.create(SESSION_CODE, NOW, 30L);
        Document document = Document.create(session, originalFileName,
                new StoredFile("stored.pdf", "sessions/x/documents/stored.pdf"), type, size, NOW);
        Field field = Document.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(document, DOCUMENT_ID);
        return document;
    }

    @Test
    @DisplayName("POST /documents 정상 업로드 → 201")
    void uploadReturns201() throws Exception {
        given(documentService.upload(anyString(), anyList()))
                .willReturn(List.of(document("운영체제_1주차.pdf", DocumentFileType.PDF, 2481920)));

        mockMvc.perform(multipart("/api/sessions/{sessionCode}/documents", SESSION_CODE)
                        .file(filePart("운영체제_1주차.pdf", "application/pdf")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documents").isArray())
                .andExpect(jsonPath("$.documents[0].id").value(DOCUMENT_ID.toString()))
                .andExpect(jsonPath("$.documents[0].originalFileName").value("운영체제_1주차.pdf"))
                .andExpect(jsonPath("$.documents[0].fileType").value("PDF"))
                .andExpect(jsonPath("$.documents[0].fileSize").value(2481920))
                .andExpect(jsonPath("$.documents[0].status").value("UPLOADED"))
                // 저장 경로와 실제 파일명은 응답에 노출하지 않는다.
                .andExpect(jsonPath("$.documents[0].storagePath").doesNotExist())
                .andExpect(jsonPath("$.documents[0].storedFileName").doesNotExist());
    }

    @Test
    @DisplayName("POST /documents 여러 파일 업로드 → 201")
    void uploadMultipleReturns201() throws Exception {
        given(documentService.upload(anyString(), anyList())).willReturn(List.of(
                document("1주차.pdf", DocumentFileType.PDF, 100),
                document("정리.docx", DocumentFileType.DOCX, 200)));

        mockMvc.perform(multipart("/api/sessions/{sessionCode}/documents", SESSION_CODE)
                        .file(filePart("1주차.pdf", "application/pdf"))
                        .file(filePart("정리.docx", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documents.length()").value(2));
    }

    @Test
    @DisplayName("POST /documents 지원하지 않는 파일 → 400 UNSUPPORTED_FILE_TYPE")
    void uploadReturns400ForUnsupportedType() throws Exception {
        willThrow(new UnsupportedFileTypeException()).given(documentService).upload(anyString(), anyList());

        mockMvc.perform(multipart("/api/sessions/{sessionCode}/documents", SESSION_CODE)
                        .file(filePart("강의.pptx", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"))
                .andExpect(jsonPath("$.message").value("PDF, DOCX, TXT 파일만 업로드할 수 있습니다."));
    }

    @Test
    @DisplayName("POST /documents 파일 개수 초과 → 400 FILE_COUNT_EXCEEDED")
    void uploadReturns400WhenFileCountExceeded() throws Exception {
        willThrow(new FileCountExceededException()).given(documentService).upload(anyString(), anyList());

        mockMvc.perform(multipart("/api/sessions/{sessionCode}/documents", SESSION_CODE)
                        .file(filePart("1.pdf", "application/pdf")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_COUNT_EXCEEDED"));
    }

    @Test
    @DisplayName("POST /documents files 파트가 없으면 → 400")
    void uploadReturns400WhenFilePartMissing() throws Exception {
        mockMvc.perform(multipart("/api/sessions/{sessionCode}/documents", SESSION_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("POST /documents 존재하지 않는 세션 → 404")
    void uploadReturns404() throws Exception {
        willThrow(new SessionNotFoundException()).given(documentService).upload(anyString(), anyList());

        mockMvc.perform(multipart("/api/sessions/{sessionCode}/documents", "ZZZZZZZZ")
                        .file(filePart("1.pdf", "application/pdf")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /documents → 200, 업로드한 파일 목록")
    void findAllReturns200() throws Exception {
        given(documentService.findAll(SESSION_CODE))
                .willReturn(List.of(document("운영체제_1주차.pdf", DocumentFileType.PDF, 2481920)));

        mockMvc.perform(get("/api/sessions/{sessionCode}/documents", SESSION_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].originalFileName").value("운영체제_1주차.pdf"))
                .andExpect(jsonPath("$.documents[0].fileType").value("PDF"))
                .andExpect(jsonPath("$.documents[0].status").value("UPLOADED"))
                .andExpect(jsonPath("$.documents[0].createdAt").value("2026-08-27T16:30:00"));
    }

    @Test
    @DisplayName("GET /documents 업로드한 파일이 없으면 → 200, 빈 배열")
    void findAllReturnsEmptyArray() throws Exception {
        given(documentService.findAll(SESSION_CODE)).willReturn(List.of());

        mockMvc.perform(get("/api/sessions/{sessionCode}/documents", SESSION_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").isArray())
                .andExpect(jsonPath("$.documents.length()").value(0));
    }

    @Test
    @DisplayName("DELETE /documents/{id} → 204")
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/sessions/{sessionCode}/documents/{documentId}", SESSION_CODE, DOCUMENT_ID))
                .andExpect(status().isNoContent());

        verify(documentService).delete(SESSION_CODE, DOCUMENT_ID);
    }

    @Test
    @DisplayName("DELETE 다른 세션의 문서 → 404 (403이 아니라 404로 존재를 감춘다)")
    void deleteReturns404ForOtherSessionDocument() throws Exception {
        willThrow(new DocumentNotFoundException()).given(documentService).delete(anyString(), any(UUID.class));

        mockMvc.perform(delete("/api/sessions/{sessionCode}/documents/{documentId}", SESSION_CODE, DOCUMENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("해당 강의자료를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("DELETE documentId가 UUID 형식이 아니면 → 400")
    void deleteReturns400ForMalformedId() throws Exception {
        mockMvc.perform(delete("/api/sessions/{sessionCode}/documents/{documentId}", SESSION_CODE, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
