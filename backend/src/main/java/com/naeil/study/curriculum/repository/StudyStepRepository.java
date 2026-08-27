package com.naeil.study.curriculum.repository;

import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.entity.StudyStepStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyStepRepository extends JpaRepository<StudyStep, UUID> {

    /**
     * 계획의 단계를 순서대로 조회한다. Topic을 함께 가져온다.
     *
     * <p>응답 변환은 트랜잭션 밖에서 일어나고, 그때 Topic의 중요도를 읽는다.
     * 지연 로딩 상태로 두면 그 시점에 초기화가 필요해져 예외가 난다.
     * 단계 수만큼 추가 조회가 나가는 것도 막는다.
     *
     * <p>{@code REVIEW} 단계는 Topic이 없으므로 left join 이어야 한다.
     */
    @Query("select step from StudyStep step "
            + "left join fetch step.topic "
            + "where step.curriculum.id = :curriculumId "
            + "order by step.stepOrder asc")
    List<StudyStep> findAllByCurriculumIdOrderByStepOrderAsc(@Param("curriculumId") UUID curriculumId);

    /**
     * 계획에 속한 단계를 조회한다.
     *
     * <p>단계 id만으로 찾지 않는다. 세션 코드로 찾은 계획에 실제로 속해 있는지 함께 확인해야
     * 다른 세션의 단계를 진행시킬 수 없다.
     */
    Optional<StudyStep> findByIdAndCurriculumId(UUID id, UUID curriculumId);

    /**
     * 계획 안에서 해당 상태의 가장 앞선 단계를 찾는다.
     *
     * <p>두 곳에 쓴다.
     * <ul>
     *   <li>{@code PENDING} — 지금 시작할 수 있는 단계 / 완료 후 다음 단계</li>
     *   <li>{@code IN_PROGRESS} — 이미 진행 중인 단계가 있는지 확인</li>
     * </ul>
     */
    Optional<StudyStep> findFirstByCurriculumIdAndStatusOrderByStepOrderAsc(
            UUID curriculumId, StudyStepStatus status);

    /**
     * 세션의 계획에서 특정 Topic 을 학습하는 단계를 찾는다.
     *
     * <p>퀴즈 생성 조건 확인에 쓴다. 그 Topic 의 학습을 완료했는지는 이 단계의 상태로 판단한다.
     * Topic 이 계획에 아예 들어가지 못한 경우(시간 부족으로 선택되지 않음)는 비어 있다.
     */
    Optional<StudyStep> findFirstByCurriculumStudySessionIdAndTopicId(UUID sessionId, UUID topicId);
}
