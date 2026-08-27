package com.naeil.study.curriculum.entity;

import com.naeil.study.curriculum.exception.InvalidStudyStepOrderException;
import com.naeil.study.curriculum.exception.StudyStepAlreadyCompletedException;
import com.naeil.study.curriculum.exception.StudyStepNotStartedException;
import com.naeil.study.topic.entity.Topic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 학습 계획의 한 단계.
 *
 * <p><b>시간 값 세 개를 분리한다.</b> 이 구분이 이후 동적 재조정의 근거가 된다.
 *
 * <pre>
 * originalEstimatedMinutes  Topic을 제대로 학습하는 데 필요하다고 본 시간 (계획 시점 복사본)
 * allocatedMinutes          이번 계획에서 실제로 배정한 시간
 * actualStudyMinutes        사용자가 실제로 쓴 시간 (8단계에서 기록)
 * </pre>
 *
 * 예: 예상 60분인 Topic에 시간이 모자라 40분을 배정했고, 실제로 52분이 걸렸다면
 * "계획보다 12분 초과"를 알 수 있다. 세 값을 하나로 합치면 이 판단이 불가능해진다.
 *
 * <p>Topic 복사본을 두는 이유는 Topic이 재분석으로 교체될 수 있기 때문이다.
 * 계획을 세운 시점의 기준값이 남아 있어야 계획 대비 실제를 비교할 수 있다.
 *
 * <p>{@code REVIEW} 단계는 Topic 하나에 묶이지 않으므로 {@code topic}이 비어 있다.
 */
@Entity
@Table(name = "study_steps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyStep {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_id", nullable = false, updatable = false)
    private Curriculum curriculum;

    /** 학습 대상 Topic. {@code REVIEW} 단계에는 없다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", updatable = false)
    private Topic topic;

    /** 계획 안에서의 순서. 1부터 시작한다. */
    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private StudyStepType type;

    /** 화면에 보여줄 제목. Topic 제목을 복사하거나 복습 단계 이름을 넣는다. */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 이번 계획에서 배정한 시간(분). 남은 시간에 맞춰 줄어들 수 있다. */
    @Column(name = "allocated_minutes", nullable = false)
    private int allocatedMinutes;

    /** 계획 시점 Topic의 권장 학습시간(분). 이후 바뀌지 않는다. */
    @Column(name = "original_estimated_minutes", nullable = false, updatable = false)
    private int originalEstimatedMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StudyStepStatus status;

    /**
     * 사용자가 반드시 학습하겠다고 밝힌 범위인지.
     *
     * <p>이후 시간이 부족해 계획을 다시 짤 때 이 단계는 가능한 한 남긴다.
     */
    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory;

    /**
     * 이 단계가 계획에 포함된 이유. PostgreSQL에서는 jsonb로 저장된다.
     *
     * <p>화면에 "기출 관련", "취약 영역" 처럼 보여주기 위한 값이다.
     * 계산에 쓰지 않는다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "priority_reasons", nullable = false)
    private List<PriorityReason> priorityReasons;

    /** 학습을 시작한 시각. 8단계에서 기록한다. */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 학습을 마친 시각. 8단계에서 기록한다. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 실제로 쓴 시간(분). 8단계에서 기록한다. */
    @Column(name = "actual_study_minutes")
    private Integer actualStudyMinutes;

    /**
     * {@code SKIPPED} 가 된 이유. 그 밖의 상태에서는 비어 있다.
     *
     * <p>9단계 동적 재조정에서 남은 시간이 부족해 제외된 단계에 {@link SkipReason#TIME_CONSTRAINT}
     * 를 남긴다. 사용자가 직접 건너뛴 것과 구분하기 위한 값이다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "skip_reason", length = 30)
    private SkipReason skipReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private StudyStep(
            Curriculum curriculum,
            Topic topic,
            int stepOrder,
            StudyStepType type,
            String title,
            int allocatedMinutes,
            int originalEstimatedMinutes,
            boolean mandatory,
            List<PriorityReason> priorityReasons,
            LocalDateTime now
    ) {
        this.curriculum = curriculum;
        this.topic = topic;
        this.stepOrder = stepOrder;
        this.type = type;
        this.title = title;
        this.allocatedMinutes = allocatedMinutes;
        this.originalEstimatedMinutes = originalEstimatedMinutes;
        this.status = StudyStepStatus.PENDING;
        this.mandatory = mandatory;
        this.priorityReasons = priorityReasons;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Topic 학습 단계를 만든다. 상태는 항상 {@link StudyStepStatus#PENDING}으로 시작한다.
     *
     * <p>{@code startedAt}, {@code completedAt}, {@code actualStudyMinutes}는 비어 있다.
     * 학습을 실제로 진행할 때 채운다.
     */
    public static StudyStep study(
            Curriculum curriculum,
            Topic topic,
            int stepOrder,
            String title,
            int allocatedMinutes,
            int originalEstimatedMinutes,
            boolean mandatory,
            List<PriorityReason> priorityReasons,
            LocalDateTime now
    ) {
        return new StudyStep(curriculum, topic, stepOrder, StudyStepType.STUDY, title,
                allocatedMinutes, originalEstimatedMinutes, mandatory, priorityReasons, now);
    }

    /**
     * 마지막 복습 단계를 만든다. Topic에 묶이지 않는다.
     *
     * <p>권장 학습시간이라는 개념이 없으므로 배정 시간을 그대로 쓴다.
     */
    public static StudyStep review(
            Curriculum curriculum,
            int stepOrder,
            String title,
            int allocatedMinutes,
            LocalDateTime now
    ) {
        return new StudyStep(curriculum, null, stepOrder, StudyStepType.REVIEW, title,
                allocatedMinutes, allocatedMinutes, false, List.of(), now);
    }

    public boolean isPending() {
        return status == StudyStepStatus.PENDING;
    }

    public boolean isInProgress() {
        return status == StudyStepStatus.IN_PROGRESS;
    }

    public boolean isCompleted() {
        return status == StudyStepStatus.COMPLETED;
    }

    public boolean isSkipped() {
        return status == StudyStepStatus.SKIPPED;
    }

    /**
     * 동적 재조정으로 배정 시간을 바꾼다.
     *
     * <p>{@code PENDING} 단계에만 허용한다. 이미 완료했거나 진행 중인 단계의 시간을 재조정으로
     * 바꾸면 실제 학습 기록과 어긋난다. 서비스가 대상을 걸러 넘기지만, 여기서도 마지막으로 막는다.
     *
     * @throws InvalidStudyStepOrderException {@code PENDING} 이 아닌 단계인 경우
     */
    public void reallocate(int allocatedMinutes, LocalDateTime now) {
        if (!isPending()) {
            throw new InvalidStudyStepOrderException();
        }
        this.allocatedMinutes = allocatedMinutes;
        this.updatedAt = now;
    }

    /**
     * 남은 시간이 부족해 이 단계를 계획에서 제외한다.
     *
     * <p>배정 시간을 0으로 만든다. {@code PENDING} 단계 배정 시간의 합이 남은 학습 시간을
     * 넘지 않아야 하므로, 수행할 수 없게 된 단계는 시간을 반납한다.
     * {@code originalEstimatedMinutes} 는 그대로 둔다. 나중에 계획 대비 실제를 비교할 근거다.
     *
     * @throws InvalidStudyStepOrderException {@code PENDING} 이 아닌 단계인 경우
     */
    public void skip(SkipReason reason, LocalDateTime now) {
        if (!isPending()) {
            throw new InvalidStudyStepOrderException();
        }
        this.status = StudyStepStatus.SKIPPED;
        this.allocatedMinutes = 0;
        this.skipReason = reason;
        this.updatedAt = now;
    }

    /**
     * 학습을 시작한다.
     *
     * <p>{@code PENDING}일 때만 전이한다. 이미 진행 중인 단계를 다시 시작하면
     * {@code startedAt}이 덮여 앞서 공부한 시간이 사라지므로, 그 경우는 호출하기 전에
     * 서비스가 걸러 낸다. 여기서는 잘못된 호출을 막는 마지막 방어선이다.
     *
     * @throws StudyStepAlreadyCompletedException 이미 완료한 단계인 경우
     * @throws InvalidStudyStepOrderException     그 밖에 시작할 수 없는 상태인 경우
     */
    public void start(LocalDateTime now) {
        if (isCompleted()) {
            throw new StudyStepAlreadyCompletedException();
        }
        if (!isPending()) {
            throw new InvalidStudyStepOrderException();
        }
        this.startedAt = now;
        this.status = StudyStepStatus.IN_PROGRESS;
        this.updatedAt = now;
    }

    /**
     * 학습을 마치고 실제 학습시간을 계산한다.
     *
     * <p>실제 학습시간은 <b>서버가 계산한다.</b> 클라이언트가 보낸 값을 믿지 않는다.
     * 화면의 타이머는 표시용이고, 기준은 {@code startedAt} 과 {@code completedAt} 이다.
     *
     * <p>배정 시간을 넘겨도 그대로 기록한다. 잘라서 저장하면 "계획보다 얼마나 오래 걸렸는가"를
     * 알 수 없게 되고, 그 값이 이후 재조정의 근거이기 때문이다.
     *
     * @throws StudyStepNotStartedException 시작 기록이 없는 단계인 경우
     */
    public void complete(LocalDateTime now) {
        if (!isInProgress() || startedAt == null) {
            throw new StudyStepNotStartedException();
        }
        this.completedAt = now;
        this.actualStudyMinutes = elapsedMinutes(startedAt, now);
        this.status = StudyStepStatus.COMPLETED;
        this.updatedAt = now;
    }

    /**
     * 경과 시간을 분으로 올림한다.
     *
     * <p>{@code ChronoUnit.MINUTES} 로 버림하면 40초 공부한 단계가 0분으로 남는다.
     * 실제로 학습한 단계가 0분으로 기록되면 이후 재조정이 "시간을 전혀 쓰지 않았다"고 판단한다.
     * 그래서 1초라도 지났으면 1분으로 센다.
     *
     * <p>시계가 뒤로 간 경우(음수)는 0으로 둔다. 음수 학습시간을 저장하지 않는다.
     */
    private static int elapsedMinutes(LocalDateTime startedAt, LocalDateTime completedAt) {
        long seconds = Duration.between(startedAt, completedAt).getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        return (int) ((seconds + 59) / 60);
    }
}
