package com.naeil.study.topic.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.session.entity.StudySession;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@DisplayName("TopicRepository - Topic 영속화")
class TopicRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 18, 0, 0);
    private static final UUID DOC_1_ID = UUID.fromString("11111111-0000-4000-8000-000000000001");
    private static final UUID DOC_2_ID = UUID.fromString("11111111-0000-4000-8000-000000000002");

    @Autowired
    private TopicRepository topicRepository;

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

    private Topic save(StudySession owner, String title, int order) {
        return topicRepository.saveAndFlush(Topic.create(owner, title, "요약",
                List.of("FCFS", "SJF"), TopicImportance.HIGH, 35,
                true, false, true, false, List.of(DOC_1_ID, DOC_2_ID), order, NOW));
    }

    @Test
    @DisplayName("저장한 Topic을 되읽으면 JSON 컬럼까지 그대로 복원된다")
    void savesAndRestoresJsonColumns() {
        Topic saved = save(session, "CPU 스케줄링", 1);
        entityManager.clear();

        Topic found = topicRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getTitle()).isEqualTo("CPU 스케줄링");
        assertThat(found.getSummary()).isEqualTo("요약");
        assertThat(found.getKeyPoints()).containsExactly("FCFS", "SJF");
        assertThat(found.getSourceDocumentIds()).containsExactly(DOC_1_ID, DOC_2_ID);
        assertThat(found.getImportance()).isEqualTo(TopicImportance.HIGH);
        assertThat(found.getEstimatedStudyMinutes()).isEqualTo(35);
        assertThat(found.isProfessorEmphasisMatched()).isTrue();
        assertThat(found.isPastExamMatched()).isFalse();
        assertThat(found.isWeakAreaMatched()).isTrue();
        assertThat(found.isMustStudyMatched()).isFalse();
        assertThat(found.getStudySession().getId()).isEqualTo(session.getId());
    }

    @Test
    @DisplayName("topicOrder 순으로 조회한다")
    void findsAllOrderedByTopicOrder() {
        save(session, "교착상태", 3);
        save(session, "프로세스와 스레드", 1);
        save(session, "CPU 스케줄링", 2);
        save(otherSession, "남의 주제", 1);
        entityManager.clear();

        assertThat(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(session.getId()))
                .extracting(Topic::getTitle)
                .containsExactly("프로세스와 스레드", "CPU 스케줄링", "교착상태");
    }

    @Test
    @DisplayName("다른 세션의 Topic은 조회되지 않는다")
    void doesNotFindTopicsOfOtherSession() {
        save(otherSession, "남의 주제", 1);
        entityManager.clear();

        assertThat(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(session.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("분석하지 않은 세션은 빈 목록이다")
    void returnsEmptyWhenNotAnalyzed() {
        assertThat(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(session.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("세션의 Topic만 골라 지운다 (재분석 시 교체)")
    void deletesOnlyOwnTopics() {
        save(session, "주제1", 1);
        save(session, "주제2", 2);
        save(otherSession, "남의 주제", 1);
        entityManager.flush();

        topicRepository.deleteAllByStudySessionId(session.getId());
        topicRepository.flush();
        entityManager.clear();

        assertThat(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(session.getId()))
                .isEmpty();
        assertThat(topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(otherSession.getId()))
                .hasSize(1);
    }

    @Test
    @DisplayName("출처 문서가 없는 Topic도 저장된다")
    void savesTopicWithoutSourceDocuments() {
        Topic saved = topicRepository.saveAndFlush(Topic.create(session, "제목", "요약",
                List.of("개념"), TopicImportance.LOW, 10,
                false, false, false, false, List.of(), 1, NOW));
        entityManager.clear();

        assertThat(topicRepository.findById(saved.getId()).orElseThrow().getSourceDocumentIds())
                .isEmpty();
    }
}
