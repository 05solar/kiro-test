package com.naeil.study.document.service;

import com.naeil.study.document.entity.Document;
import com.naeil.study.document.exception.DocumentNotFoundException;
import com.naeil.study.document.repository.DocumentRepository;
import com.naeil.study.document.validation.DocumentFileValidator;
import com.naeil.study.document.validation.DocumentFileValidator.ValidatedFile;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.storage.StorageService;
import com.naeil.study.storage.StoredFile;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 강의자료 업로드 / 조회 / 삭제 유스케이스.
 *
 * <p><b>파일 시스템과 DB는 한 트랜잭션이 아니다.</b> DB는 롤백되지만 이미 저장된 파일은
 * 저절로 사라지지 않는다. 그래서 저장 실패 시 직접 보상 삭제를 한다.
 *
 * <pre>
 * 전체 검증 → Storage 저장 → DB 저장(flush)
 *                    ↑              │ 실패
 *                    └── 보상 삭제 ─┘
 * </pre>
 *
 * <p>세션 조회는 {@link SessionService}를 통한다. 다른 도메인의 Repository를 직접 쓰지 않는다.
 * 조회와 함께 접근시각/보관기한도 갱신되므로 업로드·목록·삭제 모두 세션 활동으로 기록된다.
 */
@Service
@Transactional(readOnly = true)
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final SessionService sessionService;
    private final StorageService storageService;
    private final DocumentFileValidator documentFileValidator;
    private final Clock clock;

    public DocumentService(
            DocumentRepository documentRepository,
            SessionService sessionService,
            StorageService storageService,
            DocumentFileValidator documentFileValidator,
            Clock clock
    ) {
        this.documentRepository = documentRepository;
        this.sessionService = sessionService;
        this.storageService = storageService;
        this.documentFileValidator = documentFileValidator;
        this.clock = clock;
    }

    /**
     * 강의자료를 업로드한다. 한 요청은 전체 성공하거나 전체 실패한다.
     *
     * <p>최초 업로드에 성공하면 세션 상태가 {@code CREATED → UPLOADING}으로 바뀐다.
     */
    @Transactional
    public List<Document> upload(String sessionCode, List<MultipartFile> files) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        UUID sessionId = session.getId();

        List<ValidatedFile> validatedFiles = documentFileValidator.validate(
                files,
                documentRepository.countByStudySessionId(sessionId),
                documentRepository.sumFileSizeByStudySessionId(sessionId));

        List<StoredFile> storedFiles = storeAll(sessionId, validatedFiles);

        try {
            LocalDateTime now = LocalDateTime.now(clock);
            List<Document> documents = new ArrayList<>(validatedFiles.size());
            for (int i = 0; i < validatedFiles.size(); i++) {
                ValidatedFile validated = validatedFiles.get(i);
                documents.add(Document.create(
                        session,
                        validated.originalFileName(),
                        storedFiles.get(i),
                        validated.fileType(),
                        validated.size(),
                        now));
            }
            // 커밋 시점이 아니라 여기서 실패를 확인해야 아래 catch에서 보상 삭제를 할 수 있다.
            List<Document> saved = documentRepository.saveAllAndFlush(documents);
            session.startUploading();

            log.info("documents uploaded: sessionId={}, count={}", sessionId, saved.size());
            return saved;
        } catch (RuntimeException e) {
            log.error("failed to save document metadata, compensating storage: sessionId={}, count={}",
                    sessionId, storedFiles.size(), e);
            deleteQuietly(storedFiles);
            throw e;
        }
    }

    /** 세션에 업로드된 강의자료를 업로드 순서대로 조회한다. */
    @Transactional
    public List<Document> findAll(String sessionCode) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        return documentRepository.findAllByStudySessionIdOrderByCreatedAtAsc(session.getId());
    }

    /**
     * 강의자료를 삭제한다.
     *
     * <p>세션 ID를 조건에 함께 걸어 조회하므로 다른 세션의 문서는 애초에 찾히지 않는다.
     *
     * <p>DB를 먼저 지우고 Storage를 지운다. 반대 순서라면 DB 삭제가 실패했을 때
     * 파일 없는 메타데이터가 남는다. 지금 순서에서는 최악의 경우 참조되지 않는 파일만 남고,
     * 그 파일은 세션 만료 시 함께 정리된다.
     */
    @Transactional
    public void delete(String sessionCode, UUID documentId) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        Document document = documentRepository.findByIdAndStudySessionId(documentId, session.getId())
                .orElseThrow(DocumentNotFoundException::new);

        String storagePath = document.getStoragePath();
        documentRepository.delete(document);
        documentRepository.flush();
        storageService.delete(storagePath);

        log.info("document deleted: sessionId={}, documentId={}", session.getId(), documentId);
    }

    /**
     * 검증을 마친 파일들을 Storage에 저장한다.
     *
     * <p>중간에 하나라도 실패하면 이미 저장한 파일을 지우고 예외를 그대로 올린다.
     */
    private List<StoredFile> storeAll(UUID sessionId, List<ValidatedFile> validatedFiles) {
        List<StoredFile> storedFiles = new ArrayList<>(validatedFiles.size());
        try {
            for (ValidatedFile validated : validatedFiles) {
                storedFiles.add(storageService.save(
                        sessionId, validated.file(), validated.fileType().getExtension()));
            }
            return storedFiles;
        } catch (RuntimeException e) {
            log.error("failed to store files, compensating: sessionId={}, stored={}",
                    sessionId, storedFiles.size(), e);
            deleteQuietly(storedFiles);
            throw e;
        }
    }

    /**
     * 보상 삭제. 이미 다른 예외를 처리하는 중이므로 삭제 실패로 원인을 덮지 않는다.
     */
    private void deleteQuietly(List<StoredFile> storedFiles) {
        for (StoredFile storedFile : storedFiles) {
            try {
                storageService.delete(storedFile.storagePath());
            } catch (RuntimeException e) {
                log.error("compensating delete failed, orphan file remains: storagePath={}",
                        storedFile.storagePath(), e);
            }
        }
    }
}
