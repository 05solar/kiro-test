package com.naeil.study.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.naeil.study.storage.exception.FileStorageException;
import com.naeil.study.storage.exception.StoredFileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("LocalStorageService - 로컬 파일 저장소")
class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalStorageService storageService;
    private final UUID sessionId = UUID.fromString("6f79a1b2-0000-4000-8000-000000000001");

    @BeforeEach
    void setUp() {
        storageService = new LocalStorageService(tempDir.toString());
    }

    private MultipartFile file(String name, String content) {
        return new MockMultipartFile("files", name, "application/pdf", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("파일을 세션별 경로에 저장하고 UUID 이름을 부여한다")
    void savesFileUnderSessionPath() {
        StoredFile stored = storageService.save(sessionId, file("운영체제_1주차.pdf", "hello"), "pdf");

        assertThat(stored.storedFileName()).endsWith(".pdf");
        assertThat(stored.storagePath())
                .isEqualTo("sessions/" + sessionId + "/documents/" + stored.storedFileName());
        assertThat(tempDir.resolve(stored.storagePath())).exists();
    }

    @Test
    @DisplayName("저장 파일명에 사용자가 올린 이름을 쓰지 않는다")
    void doesNotUseOriginalFileName() {
        StoredFile stored = storageService.save(sessionId, file("../../../etc/passwd.pdf", "x"), "pdf");

        assertThat(stored.storedFileName()).doesNotContain("passwd");
        assertThat(stored.storagePath()).doesNotContain("..");
        assertThat(UUID.fromString(stored.storedFileName().replace(".pdf", ""))).isNotNull();
    }

    @Test
    @DisplayName("같은 이름의 파일을 여러 번 올려도 서로 다른 경로에 저장된다")
    void generatesDistinctPathsForSameFileName() {
        StoredFile first = storageService.save(sessionId, file("같은이름.pdf", "1"), "pdf");
        StoredFile second = storageService.save(sessionId, file("같은이름.pdf", "2"), "pdf");

        assertThat(first.storagePath()).isNotEqualTo(second.storagePath());
        assertThat(tempDir.resolve(first.storagePath())).exists();
        assertThat(tempDir.resolve(second.storagePath())).exists();
    }

    @Test
    @DisplayName("세션이 다르면 저장 경로가 분리된다")
    void separatesPathsBySession() {
        UUID otherSessionId = UUID.fromString("6f79a1b2-0000-4000-8000-000000000002");

        StoredFile mine = storageService.save(sessionId, file("a.pdf", "1"), "pdf");
        StoredFile other = storageService.save(otherSessionId, file("a.pdf", "2"), "pdf");

        assertThat(mine.storagePath()).contains(sessionId.toString());
        assertThat(other.storagePath()).contains(otherSessionId.toString());
    }

    @Test
    @DisplayName("저장한 파일을 다시 읽을 수 있다 (4단계 파서가 사용할 경로)")
    void loadsStoredFile() throws IOException {
        StoredFile stored = storageService.save(sessionId, file("메모.txt", "강의 내용"), "txt");

        try (InputStream in = storageService.load(stored.storagePath())) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("강의 내용");
        }
    }

    @Test
    @DisplayName("파일을 삭제하면 실제 파일이 사라진다")
    void deletesStoredFile() {
        StoredFile stored = storageService.save(sessionId, file("삭제대상.pdf", "x"), "pdf");
        assertThat(tempDir.resolve(stored.storagePath())).exists();

        storageService.delete(stored.storagePath());

        assertThat(tempDir.resolve(stored.storagePath())).doesNotExist();
    }

    @Test
    @DisplayName("이미 없는 파일을 삭제해도 예외가 나지 않는다")
    void deleteIsIdempotent() {
        StoredFile stored = storageService.save(sessionId, file("삭제대상.pdf", "x"), "pdf");
        storageService.delete(stored.storagePath());

        storageService.delete(stored.storagePath());

        assertThat(tempDir.resolve(stored.storagePath())).doesNotExist();
    }

    @Test
    @DisplayName("root를 벗어나는 경로는 거부한다")
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> storageService.delete("../../outside.pdf"))
                .isInstanceOf(FileStorageException.class);
        assertThatThrownBy(() -> storageService.load("../../outside.pdf"))
                .isInstanceOf(FileStorageException.class);
    }

    @Test
    @DisplayName("없는 파일을 읽으면 StoredFileNotFoundException이 발생한다")
    void throwsWhenLoadingMissingFile() {
        assertThatThrownBy(() -> storageService.load("sessions/" + sessionId + "/documents/none.pdf"))
                .isInstanceOf(StoredFileNotFoundException.class);
    }

    @Test
    @DisplayName("테스트가 끝나도 프로젝트 폴더에 파일을 남기지 않는다")
    void writesOnlyInsideTempDir() throws IOException {
        StoredFile stored = storageService.save(sessionId, file("a.pdf", "x"), "pdf");

        Path real = tempDir.resolve(stored.storagePath()).toRealPath();
        assertThat(real.startsWith(tempDir.toRealPath())).isTrue();
        assertThat(Files.exists(Path.of("./uploads/sessions/" + sessionId))).isFalse();
    }
}
