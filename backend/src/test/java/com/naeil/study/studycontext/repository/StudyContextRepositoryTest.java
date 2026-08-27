package com.naeil.study.studycontext.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.naeil.study.session.entity.StudySession;
import com.naeil.study.studycontext.entity.StudyContext;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@DisplayName("StudyContextRepository - 학습 맥락 영속화")
class StudyContextRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 17, 30, 0);

    @Autowired
    private StudyContextRepository studyContextRepository;

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

    @Test
    @DisplayName("저장한 학습 맥락을 세션 ID로 조회한다")
    void savesAndFindsBySessionId() {
        studyContextRepository.saveAndFlush(StudyContext.create(
                session, "교착상태 강조", "CPU Scheduling 기출", "가상 메모리", "교착상태", NOW));
        entityManager.clear();

        StudyContext found = studyContextRepository.findByStudySessionId(session.getId()).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getProfessorEmphasis()).isEqualTo("교착상태 강조");
        assertThat(found.getPastExamInfo()).isEqualTo("CPU Scheduling 기출");
        assertThat(found.getWeakAreas()).isEqualTo("가상 메모리");
        assertThat(found.getMustStudyAreas()).isEqualTo("교착상태");
        assertThat(found.getStudySession().getId()).isEqualTo(session.getId());
    }

    @Test
    @DisplayName("학습 맥락을 입력하지 않은 세션은 비어 있다")
    void returnsEmptyWhenAbsent() {
        assertThat(studyContextRepository.findByStudySessionId(session.getId())).isEmpty();
    }

    @Test
    @DisplayName("다른 세션의 학습 맥락은 조회되지 않는다")
    void doesNotFindContextOfOtherSession() {
        studyContextRepository.saveAndFlush(StudyContext.create(
                otherSession, "남의 강조 내용", null, null, null, NOW));
        entityManager.clear();

        assertThat(studyContextRepository.findByStudySessionId(session.getId())).isEmpty();
        assertThat(studyContextRepository.findByStudySessionId(otherSession.getId())).isPresent();
    }

    @Test
    @DisplayName("한 세션에 학습 맥락을 두 개 만들 수 없다 (UNIQUE 제약)")
    void sessionIdIsUnique() {
        studyContextRepository.saveAndFlush(StudyContext.create(session, "첫 번째", null, null, null, NOW));

        StudyContext duplicated = StudyContext.create(session, "두 번째", null, null, null, NOW);

        assertThatThrownBy(() -> studyContextRepository.saveAndFlush(duplicated))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("모든 항목이 null인 학습 맥락도 저장된다")
    void savesEmptyContext() {
        studyContextRepository.saveAndFlush(StudyContext.create(session, null, null, null, null, NOW));
        entityManager.clear();

        StudyContext found = studyContextRepository.findByStudySessionId(session.getId()).orElseThrow();

        assertThat(found.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("수정 결과가 DB에 반영되고 행이 늘어나지 않는다")
    void updateIsPersisted() {
        StudyContext saved = studyContextRepository.saveAndFlush(
                StudyContext.create(session, null, null, "가상 메모리", null, NOW));

        saved.update(null, null, "교착상태", null, NOW.plusMinutes(10));
        studyContextRepository.flush();
        entityManager.clear();

        StudyContext found = studyContextRepository.findByStudySessionId(session.getId()).orElseThrow();
        assertThat(found.getWeakAreas()).isEqualTo("교착상태");
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
        assertThat(found.getUpdatedAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(studyContextRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("2000자 텍스트를 저장할 수 있다")
    void savesLongText() {
        String longText = "가".repeat(2000);
        studyContextRepository.saveAndFlush(StudyContext.create(session, longText, null, null, null, NOW));
        entityManager.clear();

        StudyContext found = studyContextRepository.findByStudySessionId(session.getId()).orElseThrow();

        assertThat(found.getProfessorEmphasis()).hasSize(2000);
    }
}
