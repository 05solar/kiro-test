package com.naeil.study.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.entity.DocumentStatus;
import com.naeil.study.document.exception.DocumentNotFoundException;
import com.naeil.study.document.exception.UnsupportedFileTypeException;
import com.naeil.study.document.repository.DocumentRepository;
import com.naeil.study.document.validation.DocumentFileValidator;
import com.naeil.study.session.entity.SessionStatus;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.storage.StorageService;
import com.naeil.study.storage.StoredFile;
import com.naeil.study.storage.exception.FileStorageException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService - 강의자료 업로드/조회/삭제")
class DocumentServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 16, 30, 0);
    private static final UUID SESSION_ID = UUID.fromString("6f79a1b2-0000-4000-8000-000000000001");
    private static final String SESSION_CODE = "7K2M9QXF";

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private StorageService storageService;

    private DocumentService documentService;
    private StudySession session;

    @BeforeEach
    void setUp() throws Exception {
        Clock fixedClock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        documentService = new DocumentService(
                documentRepository, sessionService, storageService, new DocumentFileValidator(), fixedClock);
        session = StudySession.create(SESSION_CODE, NOW.minusHours(1), 30L);
        setSessionId(session, SESSION_ID);
    }

    /** 세션 id는 JPA가 채우므로 단위 테스트에서는 리플렉션으로 넣는다. */
    private void setSessionId(StudySession session, UUID id) throws Exception {
        Field field = StudySession.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(session, id);
    }

    private MultipartFile file(String fileName, String contentType, String content) {
        return new MockMultipartFile("files", fileName, contentType, content.getBytes(StandardCharsets.UTF_8));
    }

    private void givenSessionFound() {
        given(sessionService.getSessionAndTouch(SESSION_CODE)).willReturn(session);
    }

    private void givenEmptySession() {
        givenSessionFound();
        given(documentRepository.countByStudySessionId(SESSION_ID)).willReturn(0L);
        given(documentRepository.sumFileSizeByStudySessionId(SESSION_ID)).willReturn(0L);
    }

    private void givenStorageSavesAnything() {
        given(storageService.save(eq(SESSION_ID), any(MultipartFile.class), anyString()))
                .willAnswer(invocation -> {
                    String extension = invocation.getArgument(2);
                    String name = UUID.randomUUID() + "." + extension;
                    return new StoredFile(name, "sessions/" + SESSION_ID + "/documents/" + name);
                });
    }

    private void givenRepositorySavesAnything() {
        given(documentRepository.saveAllAndFlush(anyList())).willAnswer(i -> i.getArgument(0));
    }

    @Nested
    @DisplayName("업로드")
    class Upload {

        @Test
        @DisplayName("PDF를 업로드하면 UPLOADED 상태로 저장된다")
        void uploadsPdf() {
            givenEmptySession();
            givenStorageSavesAnything();
            givenRepositorySavesAnything();

            List<Document> documents = documentService.upload(SESSION_CODE,
                    List.of(file("운영체제_1주차.pdf", "application/pdf", "content")));

            assertThat(documents).hasSize(1);
            Document document = documents.get(0);
            assertThat(document.getOriginalFileName()).isEqualTo("운영체제_1주차.pdf");
            assertThat(document.getFileType()).isEqualTo(DocumentFileType.PDF);
            assertThat(document.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
            assertThat(document.getFileSize()).isEqualTo("content".getBytes(StandardCharsets.UTF_8).length);
            assertThat(document.getCreatedAt()).isEqualTo(NOW);
            assertThat(document.getStoredFileName()).isNotEqualTo(document.getOriginalFileName());
        }

        @Test
        @DisplayName("DOCX를 업로드할 수 있다")
        void uploadsDocx() {
            givenEmptySession();
            givenStorageSavesAnything();
            givenRepositorySavesAnything();

            List<Document> documents = documentService.upload(SESSION_CODE, List.of(file("정리.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "content")));

            assertThat(documents.get(0).getFileType()).isEqualTo(DocumentFileType.DOCX);
        }

        @Test
        @DisplayName("TXT를 업로드할 수 있다")
        void uploadsTxt() {
            givenEmptySession();
            givenStorageSavesAnything();
            givenRepositorySavesAnything();

            List<Document> documents =
                    documentService.upload(SESSION_CODE, List.of(file("메모.txt", "text/plain", "content")));

            assertThat(documents.get(0).getFileType()).isEqualTo(DocumentFileType.TXT);
        }

        @Test
        @DisplayName("여러 파일을 한 번에 업로드할 수 있다")
        void uploadsMultipleFiles() {
            givenEmptySession();
            givenStorageSavesAnything();
            givenRepositorySavesAnything();

            List<Document> documents = documentService.upload(SESSION_CODE, List.of(
                    file("1주차.pdf", "application/pdf", "a"),
                    file("2주차.pdf", "application/pdf", "bb"),
                    file("정리.docx", null, "ccc")));

            assertThat(documents).hasSize(3);
            assertThat(documents).extracting(Document::getOriginalFileName)
                    .containsExactly("1주차.pdf", "2주차.pdf", "정리.docx");
            verify(storageService, times(3)).save(eq(SESSION_ID), any(MultipartFile.class), anyString());
        }

        @Test
        @DisplayName("최초 업로드에 성공하면 세션 상태가 UPLOADING으로 바뀐다")
        void changesSessionStatusToUploading() {
            givenEmptySession();
            givenStorageSavesAnything();
            givenRepositorySavesAnything();
            assertThat(session.getStatus()).isEqualTo(SessionStatus.CREATED);

            documentService.upload(SESSION_CODE, List.of(file("a.pdf", "application/pdf", "x")));

            assertThat(session.getStatus()).isEqualTo(SessionStatus.UPLOADING);
        }

        @Test
        @DisplayName("업로드는 세션 접근으로 기록된다")
        void refreshesSessionAccessTime() {
            givenEmptySession();
            givenStorageSavesAnything();
            givenRepositorySavesAnything();

            documentService.upload(SESSION_CODE, List.of(file("a.pdf", "application/pdf", "x")));

            verify(sessionService).getSessionAndTouch(SESSION_CODE);
        }

        @Test
        @DisplayName("검증에 실패하면 Storage에 아무것도 저장하지 않는다")
        void doesNotTouchStorageWhenValidationFails() {
            givenEmptySession();

            assertThatThrownBy(() -> documentService.upload(SESSION_CODE, List.of(
                    file("정상.pdf", "application/pdf", "a"),
                    file("악성.exe", null, "b"))))
                    .isInstanceOf(UnsupportedFileTypeException.class);

            verify(storageService, never()).save(any(), any(), anyString());
            verify(documentRepository, never()).saveAllAndFlush(anyList());
        }

        @Test
        @DisplayName("Storage 저장 중 실패하면 이미 저장한 파일을 지운다")
        void compensatesStorageWhenSaveFails() {
            givenEmptySession();
            StoredFile first = new StoredFile("first.pdf", "sessions/" + SESSION_ID + "/documents/first.pdf");
            given(storageService.save(eq(SESSION_ID), any(MultipartFile.class), anyString()))
                    .willReturn(first)
                    .willThrow(new FileStorageException());

            assertThatThrownBy(() -> documentService.upload(SESSION_CODE, List.of(
                    file("1.pdf", "application/pdf", "a"),
                    file("2.pdf", "application/pdf", "b"))))
                    .isInstanceOf(FileStorageException.class);

            verify(storageService).delete(first.storagePath());
            verify(documentRepository, never()).saveAllAndFlush(anyList());
        }

        @Test
        @DisplayName("DB 저장에 실패하면 저장한 파일을 모두 지운다")
        void compensatesStorageWhenDatabaseSaveFails() {
            givenEmptySession();
            givenStorageSavesAnything();
            given(documentRepository.saveAllAndFlush(anyList()))
                    .willThrow(new DataIntegrityViolationException("boom"));

            assertThatThrownBy(() -> documentService.upload(SESSION_CODE, List.of(
                    file("1.pdf", "application/pdf", "a"),
                    file("2.pdf", "application/pdf", "b"))))
                    .isInstanceOf(DataIntegrityViolationException.class);

            verify(storageService, times(2)).delete(anyString());
        }

        @Test
        @DisplayName("보상 삭제가 실패해도 원래 예외를 그대로 올린다")
        void keepsOriginalExceptionWhenCompensationFails() {
            givenEmptySession();
            givenStorageSavesAnything();
            given(documentRepository.saveAllAndFlush(anyList()))
                    .willThrow(new DataIntegrityViolationException("boom"));
            willThrow(new FileStorageException()).given(storageService).delete(anyString());

            assertThatThrownBy(() -> documentService.upload(SESSION_CODE,
                    List.of(file("1.pdf", "application/pdf", "a"))))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("존재하지 않는 세션이면 SessionNotFoundException이 발생한다")
        void throwsWhenSessionNotFound() {
            given(sessionService.getSessionAndTouch("ZZZZZZZZ")).willThrow(new SessionNotFoundException());

            assertThatThrownBy(() -> documentService.upload("ZZZZZZZZ",
                    List.of(file("a.pdf", "application/pdf", "x"))))
                    .isInstanceOf(SessionNotFoundException.class);

            verify(storageService, never()).save(any(), any(), anyString());
        }
    }

    @Nested
    @DisplayName("목록 조회")
    class FindAll {

        @Test
        @DisplayName("업로드 순서대로 조회한다")
        void findsAllInUploadOrder() {
            givenSessionFound();
            Document document = Document.create(session, "1주차.pdf",
                    new StoredFile("a.pdf", "sessions/x/documents/a.pdf"), DocumentFileType.PDF, 10, NOW);
            given(documentRepository.findAllByStudySessionIdOrderByCreatedAtAsc(SESSION_ID))
                    .willReturn(List.of(document));

            List<Document> documents = documentService.findAll(SESSION_CODE);

            assertThat(documents).containsExactly(document);
        }

        @Test
        @DisplayName("업로드한 파일이 없으면 빈 목록을 돌려준다")
        void returnsEmptyList() {
            givenSessionFound();
            given(documentRepository.findAllByStudySessionIdOrderByCreatedAtAsc(SESSION_ID))
                    .willReturn(List.of());

            assertThat(documentService.findAll(SESSION_CODE)).isEmpty();
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        private final UUID documentId = UUID.fromString("11111111-0000-4000-8000-000000000001");

        @Test
        @DisplayName("DB와 Storage에서 모두 제거한다")
        void deletesFromDatabaseAndStorage() {
            givenSessionFound();
            Document document = Document.create(session, "1주차.pdf",
                    new StoredFile("a.pdf", "sessions/x/documents/a.pdf"), DocumentFileType.PDF, 10, NOW);
            given(documentRepository.findByIdAndStudySessionId(documentId, SESSION_ID))
                    .willReturn(Optional.of(document));

            documentService.delete(SESSION_CODE, documentId);

            verify(documentRepository).delete(document);
            verify(storageService).delete("sessions/x/documents/a.pdf");
        }

        @Test
        @DisplayName("세션 ID를 함께 조건으로 조회한다 (다른 세션 문서 접근 방지)")
        void queriesWithSessionId() {
            givenSessionFound();
            given(documentRepository.findByIdAndStudySessionId(documentId, SESSION_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> documentService.delete(SESSION_CODE, documentId))
                    .isInstanceOf(DocumentNotFoundException.class);

            verify(documentRepository).findByIdAndStudySessionId(documentId, SESSION_ID);
            verify(storageService, never()).delete(anyString());
        }

        @Test
        @DisplayName("존재하지 않는 문서면 DocumentNotFoundException이 발생하고 Storage를 건드리지 않는다")
        void throwsWhenDocumentNotFound() {
            givenSessionFound();
            given(documentRepository.findByIdAndStudySessionId(documentId, SESSION_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> documentService.delete(SESSION_CODE, documentId))
                    .isInstanceOf(DocumentNotFoundException.class);

            verify(documentRepository, never()).delete(any(Document.class));
            verify(storageService, never()).delete(anyString());
        }
    }
}
