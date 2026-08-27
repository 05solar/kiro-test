package com.naeil.study.document.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.entity.DocumentStatus;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.storage.StoredFile;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@DisplayName("DocumentRepository - 강의자료 영속화")
class DocumentRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 16, 30, 0);

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private EntityManager entityManager;

    private StudySession session;
    private StudySession otherSession;

    @BeforeEach
    void setUp() {
        session = StudySession.create("7K2M9QXF", NOW, 30L);
        otherSession = StudySession.create("ABCDEFGH", NOW, 30L);
        entityManager.persist(session);
        entityManager.persist(otherSession);
        entityManager.flush();
    }

    private Document save(StudySession owner, String fileName, long size, LocalDateTime createdAt) {
        String stored = UUID.randomUUID() + ".pdf";
        Document document = Document.create(owner, fileName,
                new StoredFile(stored, "sessions/" + owner.getId() + "/documents/" + stored),
                DocumentFileType.PDF, size, createdAt);
        return documentRepository.saveAndFlush(document);
    }

    @Test
    @DisplayName("저장한 문서는 UUID가 발급되고 UPLOADED 상태다")
    void savesDocument() {
        Document saved = save(session, "운영체제_1주차.pdf", 100, NOW);
        entityManager.clear();

        Document found = documentRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getOriginalFileName()).isEqualTo("운영체제_1주차.pdf");
        assertThat(found.getFileType()).isEqualTo(DocumentFileType.PDF);
        assertThat(found.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(found.getFileSize()).isEqualTo(100);
        assertThat(found.getStudySession().getId()).isEqualTo(session.getId());
    }

    @Test
    @DisplayName("세션의 문서를 업로드 순서대로 조회한다")
    void findsAllOrderedByCreatedAt() {
        save(session, "3주차.pdf", 100, NOW.plusMinutes(2));
        save(session, "1주차.pdf", 100, NOW);
        save(session, "2주차.pdf", 100, NOW.plusMinutes(1));
        save(otherSession, "남의자료.pdf", 100, NOW);
        entityManager.clear();

        assertThat(documentRepository.findAllByStudySessionIdOrderByCreatedAtAsc(session.getId()))
                .extracting(Document::getOriginalFileName)
                .containsExactly("1주차.pdf", "2주차.pdf", "3주차.pdf");
    }

    @Test
    @DisplayName("다른 세션의 문서는 ID를 알아도 조회되지 않는다")
    void doesNotFindDocumentOfOtherSession() {
        Document othersDocument = save(otherSession, "남의자료.pdf", 100, NOW);
        entityManager.clear();

        assertThat(documentRepository.findByIdAndStudySessionId(othersDocument.getId(), session.getId()))
                .isEmpty();
        assertThat(documentRepository.findByIdAndStudySessionId(othersDocument.getId(), otherSession.getId()))
                .isPresent();
    }

    @Test
    @DisplayName("세션별 파일 개수를 센다")
    void countsBySession() {
        save(session, "1.pdf", 100, NOW);
        save(session, "2.pdf", 100, NOW);
        save(otherSession, "3.pdf", 100, NOW);
        entityManager.clear();

        assertThat(documentRepository.countByStudySessionId(session.getId())).isEqualTo(2);
        assertThat(documentRepository.countByStudySessionId(otherSession.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("세션별 총 용량을 합산한다")
    void sumsFileSizeBySession() {
        save(session, "1.pdf", 1_000, NOW);
        save(session, "2.pdf", 2_500, NOW);
        save(otherSession, "3.pdf", 9_999, NOW);
        entityManager.clear();

        assertThat(documentRepository.sumFileSizeByStudySessionId(session.getId())).isEqualTo(3_500);
    }

    @Test
    @DisplayName("파일이 없는 세션의 총 용량은 0이다")
    void sumsToZeroWhenNoDocuments() {
        assertThat(documentRepository.sumFileSizeByStudySessionId(session.getId())).isZero();
    }

    @Test
    @DisplayName("삭제하면 DB에서 사라진다")
    void deletesDocument() {
        Document saved = save(session, "삭제대상.pdf", 100, NOW);

        documentRepository.delete(saved);
        documentRepository.flush();
        entityManager.clear();

        assertThat(documentRepository.findById(saved.getId())).isEmpty();
        assertThat(documentRepository.countByStudySessionId(session.getId())).isZero();
    }
}
