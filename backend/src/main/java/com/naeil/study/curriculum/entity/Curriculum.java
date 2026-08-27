package com.naeil.study.curriculum.entity;

import com.naeil.study.session.entity.StudySession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 남은 시간 안에서 실제로 수행 가능한 학습 계획.
 *
 * <p>Topic 전체를 그대로 나열한 것이 아니다. 사용자의 남은 학습 시간에 맞춰
 * 무엇을 얼마나 공부할지 정한 결과다.
 *
 * <p>세션당 하나만 존재한다({@code session_id} UNIQUE).
 * 계획을 다시 만드는 기능은 이 단계에서 구현하지 않는다.
 *
 * <p><b>시간 값 두 개를 분리해 둔 이유</b>
 * <pre>
 * initialRemainingMinutes  계획을 세운 시점의 남은 학습 시간 (기준값, 이후 불변)
 * totalAllocatedMinutes    실제 배정한 시간의 합
 * </pre>
 * 학습이 진행되면 세션의 {@code remainingStudyMinutes}는 줄어든다.
 * 그때도 "처음에 몇 분을 기준으로 계획했는가"를 알 수 있어야 계획 대비 실제를 비교할 수 있다.
 */
@Entity
@Table(
        name = "curriculums",
        uniqueConstraints = @UniqueConstraint(name = "uk_curriculums_session_id", columnNames = "session_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Curriculum {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private StudySession studySession;

    /** 계획을 세운 시점의 남은 학습 시간(분). 이후 바뀌지 않는다. */
    @Column(name = "initial_remaining_minutes", nullable = false, updatable = false)
    private int initialRemainingMinutes;

    /** 각 단계에 배정한 시간의 합. 항상 {@link #initialRemainingMinutes} 이하다. */
    @Column(name = "total_allocated_minutes", nullable = false)
    private int totalAllocatedMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CurriculumStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Curriculum(
            StudySession studySession,
            int initialRemainingMinutes,
            int totalAllocatedMinutes,
            LocalDateTime now
    ) {
        this.studySession = studySession;
        this.initialRemainingMinutes = initialRemainingMinutes;
        this.totalAllocatedMinutes = totalAllocatedMinutes;
        this.status = CurriculumStatus.CREATED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 최초 학습 계획을 만든다. 상태는 항상 {@link CurriculumStatus#CREATED}로 시작한다.
     *
     * <p>계획 자체를 만든 것만으로 학습을 시작한 것으로 보지 않는다.
     */
    public static Curriculum create(
            StudySession studySession,
            int initialRemainingMinutes,
            int totalAllocatedMinutes,
            LocalDateTime now
    ) {
        return new Curriculum(studySession, initialRemainingMinutes, totalAllocatedMinutes, now);
    }

    /**
     * 첫 단계를 실제로 시작해 학습이 진행 중이 되었다.
     *
     * <p>{@code CREATED}일 때만 전이한다. 이미 진행 중이거나 끝난 계획을 되돌리지 않는다.
     */
    public void startProgress(LocalDateTime now) {
        if (status == CurriculumStatus.CREATED) {
            this.status = CurriculumStatus.IN_PROGRESS;
            this.updatedAt = now;
        }
    }

    /**
     * 남은 단계가 없어 계획을 마쳤다.
     *
     * <p>계획이 끝났다고 세션까지 끝난 것은 아니다. 이후 퀴즈와 최종 복습이 남아 있으므로
     * 세션 상태는 서비스가 따로 판단한다.
     */
    public void completeProgress(LocalDateTime now) {
        this.status = CurriculumStatus.COMPLETED;
        this.updatedAt = now;
    }

    public boolean isCompleted() {
        return status == CurriculumStatus.COMPLETED;
    }
}
