package com.naeil.study.curriculum.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.naeil.study.curriculum.entity.Curriculum;
import com.naeil.study.curriculum.entity.CurriculumStatus;
import com.naeil.study.curriculum.entity.PriorityReason;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.entity.StudyStepStatus;
import com.naeil.study.curriculum.entity.StudyStepType;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.entity.TopicImportance;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@DisplayName("CurriculumRepository / StudyStepRepository - 학습 계획 영속화")
class CurriculumRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 18, 0, 0);

    @Autowired
    private CurriculumRepository curriculumRepository;

    @Autowired
    private StudyStepRepository studyStepRepository;

    @Autowired
    private EntityManager entityManager;

    private StudySession session;
    private StudySession otherSession;
    private Topic topic;

    @BeforeEach
    void setUp() {
        session = StudySession.create("7K2M9QXF", NOW, 30L);
        otherSession = StudySession.create("ABCDEFGH", NOW, 30L);
        entityManager.persist(session);
        entityManager.persist(otherSession);
        topic = Topic.create(session, "CPU 스케줄링", "요약", List.of("FCFS"),
                TopicImportance.VERY_HIGH, 50, false, true, false, true, List.of(), 1, NOW);
        entityManager.persist(topic);
        entityManager.flush();
    }

    private Curriculum saveCurriculum(StudySession owner, int allocated) {
        return curriculumRepository.saveAndFlush(Curriculum.create(owner, 180, allocated, NOW));
    }

    @Test
    @DisplayName("저장한 계획을 세션 ID로 조회한다")
    void savesAndFindsBySessionId() {
        saveCurriculum(session, 175);
        entityManager.clear();

        Curriculum found = curriculumRepository.findByStudySessionId(session.getId()).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getInitialRemainingMinutes()).isEqualTo(180);
        assertThat(found.getTotalAllocatedMinutes()).isEqualTo(175);
        assertThat(found.getStatus()).isEqualTo(CurriculumStatus.CREATED);
    }

    @Test
    @DisplayName("계획을 만들지 않은 세션은 비어 있다")
    void returnsEmptyWhenAbsent() {
        assertThat(curriculumRepository.findByStudySessionId(session.getId())).isEmpty();
    }

    @Test
    @DisplayName("다른 세션의 계획은 조회되지 않는다")
    void doesNotFindCurriculumOfOtherSession() {
        saveCurriculum(otherSession, 100);
        entityManager.clear();

        assertThat(curriculumRepository.findByStudySessionId(session.getId())).isEmpty();
        assertThat(curriculumRepository.findByStudySessionId(otherSession.getId())).isPresent();
    }

    @Test
    @DisplayName("한 세션에 계획을 두 개 만들 수 없다 (UNIQUE 제약)")
    void sessionIdIsUnique() {
        saveCurriculum(session, 175);

        Curriculum duplicated = Curriculum.create(session, 180, 100, NOW);

        assertThatThrownBy(() -> curriculumRepository.saveAndFlush(duplicated))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("학습 단계를 순서대로 조회하고 JSON 컬럼까지 복원한다")
    void savesAndRestoresStudySteps() {
        Curriculum curriculum = saveCurriculum(session, 175);
        studyStepRepository.saveAndFlush(StudyStep.review(curriculum, 2, "핵심 개념 최종 복습", 135, NOW));
        studyStepRepository.saveAndFlush(StudyStep.study(curriculum, topic, 1, "CPU 스케줄링",
                40, 50, true, List.of(PriorityReason.MUST_STUDY, PriorityReason.CORE_TOPIC), NOW));
        entityManager.clear();

        List<StudyStep> steps =
                studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(curriculum.getId());

        assertThat(steps).hasSize(2);
        StudyStep first = steps.get(0);
        assertThat(first.getStepOrder()).isEqualTo(1);
        assertThat(first.getType()).isEqualTo(StudyStepType.STUDY);
        assertThat(first.getTitle()).isEqualTo("CPU 스케줄링");
        assertThat(first.getAllocatedMinutes()).isEqualTo(40);
        assertThat(first.getOriginalEstimatedMinutes()).isEqualTo(50);
        assertThat(first.getStatus()).isEqualTo(StudyStepStatus.PENDING);
        assertThat(first.isMandatory()).isTrue();
        assertThat(first.getPriorityReasons())
                .containsExactly(PriorityReason.MUST_STUDY, PriorityReason.CORE_TOPIC);
        assertThat(first.getTopic().getId()).isEqualTo(topic.getId());

        StudyStep second = steps.get(1);
        assertThat(second.getType()).isEqualTo(StudyStepType.REVIEW);
        assertThat(second.getTopic()).isNull();
        assertThat(second.getPriorityReasons()).isEmpty();
    }

    @Test
    @DisplayName("새로 만든 단계의 진행 기록은 비어 있다")
    void newStepHasNoProgressRecord() {
        Curriculum curriculum = saveCurriculum(session, 40);
        StudyStep saved = studyStepRepository.saveAndFlush(
                StudyStep.study(curriculum, topic, 1, "CPU 스케줄링", 40, 50, false, List.of(), NOW));
        entityManager.clear();

        StudyStep found = studyStepRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getStartedAt()).isNull();
        assertThat(found.getCompletedAt()).isNull();
        assertThat(found.getActualStudyMinutes()).isNull();
    }

    @Test
    @DisplayName("다른 계획의 단계는 조회되지 않는다")
    void doesNotFindStepsOfOtherCurriculum() {
        Curriculum mine = saveCurriculum(session, 40);
        Curriculum other = saveCurriculum(otherSession, 40);
        studyStepRepository.saveAndFlush(StudyStep.review(other, 1, "남의 복습", 40, NOW));
        entityManager.clear();

        assertThat(studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(mine.getId()))
                .isEmpty();
        assertThat(studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(other.getId()))
                .hasSize(1);
    }
}
