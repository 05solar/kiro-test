package com.naeil.study.storage;

import com.naeil.study.storage.exception.FileStorageException;
import com.naeil.study.storage.exception.StoredFileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬 파일 시스템 기반 Storage 구현. 개발/MVP 용도다.
 *
 * <pre>
 * {root}/sessions/{sessionId}/documents/{uuid}.{ext}
 * </pre>
 *
 * <p>배포 시에는 이 클래스 대신 S3 호환 구현을 등록한다. 도메인 코드는 바뀌지 않는다.
 */
@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private static final String SESSIONS_DIR = "sessions";
    private static final String DOCUMENTS_DIR = "documents";

    private final Path rootPath;

    public LocalStorageService(@Value("${storage.local.root-path:./uploads}") String rootPath) {
        this.rootPath = Paths.get(rootPath).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile save(UUID sessionId, MultipartFile file, String extension) {
        String storedFileName = UUID.randomUUID() + "." + extension;
        String storagePath = String.join("/", SESSIONS_DIR, sessionId.toString(), DOCUMENTS_DIR, storedFileName);
        Path target = resolve(storagePath);

        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("failed to store file: sessionId={}, storagePath={}", sessionId, storagePath, e);
            throw new FileStorageException(e);
        }
        return new StoredFile(storedFileName, storagePath);
    }

    @Override
    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(resolve(storagePath));
        } catch (IOException e) {
            log.error("failed to delete file: storagePath={}", storagePath, e);
            throw new FileStorageException(e);
        }
    }

    @Override
    public InputStream load(String storagePath) {
        Path target = resolve(storagePath);
        if (!Files.exists(target)) {
            // DB에는 메타데이터가 있는데 파일이 없는 상황. 저장소 장애와 구분한다.
            log.warn("stored file not found: storagePath={}", storagePath);
            throw new StoredFileNotFoundException();
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            log.error("failed to read file: storagePath={}", storagePath, e);
            throw new FileStorageException(e);
        }
    }

    /**
     * 상대 경로를 root 아래의 실제 경로로 바꾼다.
     *
     * <p>정규화한 결과가 root를 벗어나면 거부한다. 저장 경로는 서버가 만들지만,
     * DB에 잘못된 값이 들어간 경우에도 root 밖의 파일을 건드리지 못하게 한다.
     */
    private Path resolve(String storagePath) {
        Path resolved = rootPath.resolve(storagePath).normalize();
        if (!resolved.startsWith(rootPath)) {
            log.error("storage path escapes root: storagePath={}", storagePath);
            throw new FileStorageException();
        }
        return resolved;
    }
}
