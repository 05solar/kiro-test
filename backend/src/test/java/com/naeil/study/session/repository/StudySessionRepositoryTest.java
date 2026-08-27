package com.naeil.study.session.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.naeil.study.session.entity.SessionStatus;
import com.naeil.study.session.entity.StudySession;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@DisplayName("StudySessionRepository - 세션 영속화")
class StudySessionRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 15, 30, 0);

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("저장한 세션은 UUID가 발급되고 코드로 조회된다")
    void savesAndFindsBySessionCode() {
        studySessionRepository.save(StudySession.create("7K2M9QXF", NOW, 30L));
        entityManager.flush();
        entityManager.clear();

        StudySession found = studySessionRepository.findBySessionCode("7K2M9QXF").orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getSessionCode()).isEqualTo("7K2M9QXF");
        assertThat(found.getStatus()).isEqualTo(SessionStatus.CREATED);
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
        assertThat(found.getExpiresAt()).isEqualTo(NOW.plusDays(30));
        // 동적 커리큘럼용 필드는 컬럼만 존재하고 값은 비어 있다.
        assertThat(found.getAvailableStudyMinutes()).isNull();
        assertThat(found.getRemainingStudyMinutes()).isNull();
        assertThat(found.getCurrentStepOrder()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 코드로 조회하면 비어 있다")
    void findBySessionCodeReturnsEmpty() {
        assertThat(studySessionRepository.findBySessionCode("ZZZZZZZZ")).isEmpty();
    }

    @Test
    @DisplayName("existsBySessionCode로 중복 여부를 확인할 수 있다")
    void existsBySessionCode() {
        studySessionRepository.save(StudySession.create("7K2M9QXF", NOW, 30L));
        entityManager.flush();

        assertThat(studySessionRepository.existsBySessionCode("7K2M9QXF")).isTrue();
        assertThat(studySessionRepository.existsBySessionCode("ZZZZZZZZ")).isFalse();
    }

    @Test
    @DisplayName("같은 세션 코드는 UNIQUE 제약조건으로 중복 저장할 수 없다")
    void sessionCodeIsUnique() {
        studySessionRepository.saveAndFlush(StudySession.create("7K2M9QXF", NOW, 30L));

        StudySession duplicated = StudySession.create("7K2M9QXF", NOW, 30L);

        assertThatThrownBy(() -> studySessionRepository.saveAndFlush(duplicated))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("touch 결과는 DB에 반영된다")
    void touchIsPersisted() {
        StudySession saved = studySessionRepository.save(StudySession.create("7K2M9QXF", NOW, 30L));
        entityManager.flush();

        LocalDateTime accessedAt = NOW.plusDays(5);
        saved.touch(accessedAt, 30L);
        entityManager.flush();
        entityManager.clear();

        StudySession found = studySessionRepository.findBySessionCode("7K2M9QXF").orElseThrow();
        assertThat(found.getLastAccessedAt()).isEqualTo(accessedAt);
        assertThat(found.getExpiresAt()).isEqualTo(accessedAt.plusDays(30));
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
    }
}
