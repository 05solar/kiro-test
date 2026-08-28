package com.naeil.study.session.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.repository.DocumentRepository;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.repository.SessionPurgeRepository;
import com.naeil.study.session.repository.StudySessionRepository;
import com.naeil.study.storage.StorageService;
import com.naeil.study.storage.StoredFile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 만료 세션 정리 검증.
 *
 * <p>실제 DB에 붙여 확인한다. 이 기능의 위험은 로직이 아니라 <b>삭제 순서</b>에 있다.
 * 자식 테이블을 먼저 지우지 않으면 외래키 제약에 걸리는데, 그건 목으로는 드러나지 않는다.
 */
@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "session.cleanup.enabled=true"
})
@DisplayName("ExpiredSessionCleaner - 만료 세션 정리")
class ExpiredSessionCleanerTest {

    /** 어떤 파일을 지웠는지 기록만 하는 저장소. 디스크를 건드리지 않는다. */
    static class RecordingStorageService implements StorageService {

        final List<String> deleted = new ArrayList<>();

        @Override
        public StoredFile save(UUID sessionId, MultipartFile file, String extension) {
            String name = UUID.randomUUID() + "." + extension;
            return new StoredFile(name, "sessions/" + sessionId + "/documents/" + name);
        }

        @Override
        public void delete(String storagePath) {
            deleted.add(storagePath);
        }

        @Override
        public InputStream load(String storagePath) {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        StorageService storageService() {
            return new RecordingStorageService();
        }
    }

    @Autowired
    private ExpiredSessionCleaner cleaner;

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private SessionPurgeRepository sessionPurgeRepository;

    @Autowired
    private StorageService storageService;

    @Autowired
    private Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 1차 캐시를 비운다.
     *
     * <p>벌크 삭제는 영속성 컨텍스트를 거치지 않는다. 비우지 않고 findById 를 부르면
     * DB 에서 사라진 행이 캐시에서 그대로 나온다.
     */
    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    private RecordingStorageService storage() {
        return (RecordingStorageService) storageService;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    @BeforeEach
    void clearRecorded() {
        storage().deleted.clear();
    }

    /** 보관 기한이 지난 세션을 만든다. */
    private StudySession expiredSession(String code) {
        StudySession session = StudySession.create(code, now().minusDays(60), 30L);
        return studySessionRepository.saveAndFlush(session);
    }

    /** 아직 살아 있는 세션을 만든다. */
    private StudySession liveSession(String code) {
        StudySession session = StudySession.create(code, now(), 30L);
        return studySessionRepository.saveAndFlush(session);
    }

    private Document document(StudySession session, String fileName) {
        StoredFile stored = new StoredFile(
                UUID.randomUUID() + ".txt",
                "sessions/" + session.getId() + "/documents/" + fileName);
        return documentRepository.saveAndFlush(Document.create(
                session, fileName, stored, DocumentFileType.TXT, 100L, now()));
    }

    @Test
    @Transactional
    @DisplayName("보관 기한이 지난 세션을 지우고 살아 있는 세션은 남긴다")
    void deletesOnlyExpiredSessions() {
        StudySession expired = expiredSession("EXPIRED1");
        StudySession live = liveSession("LIVEONE1");

        int deleted = cleaner.deleteExpired();
        reload();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(studySessionRepository.findById(expired.getId())).isEmpty();
        assertThat(studySessionRepository.findById(live.getId())).isPresent();
    }

    @Test
    @Transactional
    @DisplayName("DB를 지우기 전에 실제 파일을 먼저 지운다")
    void deletesStoredFilesFirst() {
        StudySession expired = expiredSession("EXPIRED2");
        Document first = document(expired, "os.txt");
        Document second = document(expired, "network.txt");

        cleaner.deleteExpired();
        reload();

        assertThat(storage().deleted)
                .contains(first.getStoragePath(), second.getStoragePath());
        assertThat(documentRepository.findById(first.getId())).isEmpty();
        assertThat(documentRepository.findById(second.getId())).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("자식 데이터가 있어도 외래키 제약에 걸리지 않는다")
    void purgesChildRowsInOrder() {
        StudySession expired = expiredSession("EXPIRED3");
        document(expired, "os.txt");

        // 순서가 틀리면 여기서 제약 위반으로 터진다.
        int deleted = sessionPurgeRepository.purge(List.of(expired.getId()));
        reload();

        assertThat(deleted).isEqualTo(1);
        assertThat(studySessionRepository.findById(expired.getId())).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("지울 세션이 없으면 아무 일도 하지 않는다")
    void doesNothingWhenNothingExpired() {
        liveSession("LIVETWO1");

        assertThat(sessionPurgeRepository.purge(List.of())).isZero();
        assertThat(storage().deleted).isEmpty();
    }
}
