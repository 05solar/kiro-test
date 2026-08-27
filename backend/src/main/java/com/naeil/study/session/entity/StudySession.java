package com.naeil.study.session.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학습 세션. 이 서비스의 최상위 도메인이다.
 *
 * <p>회원 개념이 없으므로 사용자는 {@code sessionCode} 8자리만으로 자신의 학습 공간에 접근한다.
 * 내부 식별자 {@code id}(UUID)는 다른 도메인과의 연관관계에만 사용하고 외부에 노출하지 않는다.
 *
 * <p>이후 단계에서 Document / Topic / StudyStep / Quiz 연관관계가 이 엔티티에 추가된다.
 *
 * <p><b>동적 커리큘럼을 위한 시간 필드 설계</b>
 * <pre>
 * availableStudyMinutes  최초 입력한 전체 학습 가능 시간 (진행 중 불변, 기준값)
 * remainingStudyMinutes  현재 남아 있는 학습 가능 시간   (STEP 완료마다 감소, 재조정 기준)
 * currentStepOrder       현재 진행 중인 STEP 순번        (다른 기기에서 복구용)
 * </pre>
 * 두 시간 값을 분리해 두었기 때문에, 이후 STEP 완료 시
 * {@code 실제 학습시간 → remainingStudyMinutes 갱신 → 남은 STEP 시간 재배분} 흐름을
 * 이 엔티티 구조를 바꾸지 않고 추가할 수 있다.
 */
@Entity
@Table(
        name = "study_sessions",
        uniqueConstraints = @UniqueConstraint(name = "uk_study_sessions_session_code", columnNames = "session_code")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudySession {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** 사용자가 직접 입력하는 유일한 접근 키. 서버에서만 발급한다. */
    @Column(name = "session_code", nullable = false, updatable = false, length = 8)
    private String sessionCode;

    /** 과목명. 2단계(시험 정보 입력)에서 채운다. */
    @Column(name = "subject")
    private String subject;

    /** 시험 일시. 2단계(시험 정보 입력)에서 채운다. */
    @Column(name = "exam_at")
    private LocalDateTime examAt;

    /**
     * 사용자가 시험 정보 입력 시 설정한 <b>전체</b> 학습 가능 시간(분).
     *
     * <p>최초 커리큘럼 생성의 기준값이며 학습이 진행되는 동안 변경하지 않는다.
     * 진행률이나 초과/단축 학습량을 계산할 때 원본 기준으로 사용한다.
     *
     * <p>시험 정보 입력 단계(2단계)에서 채운다.
     */
    @Column(name = "available_study_minutes")
    private Integer availableStudyMinutes;

    /**
     * 현재 시점에서 <b>남아 있는</b> 학습 가능 시간(분).
     *
     * <p>시험 정보 입력 시 {@link #availableStudyMinutes}와 같은 값으로 시작하고,
     * 이후 StudyStep을 완료할 때마다 실제 학습시간만큼 줄어든다.
     * 동적 커리큘럼 재조정은 이 값을 기준으로 남은 STEP의 시간을 재배분한다.
     *
     * <p>1단계에서는 필드만 준비한다. 차감/재배분 로직은 학습 진행 단계(7단계)에서 구현한다.
     */
    @Column(name = "remaining_study_minutes")
    private Integer remainingStudyMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status;

    /**
     * 현재 진행 중인 StudyStep의 순번. 다른 기기에서 학습 상태를 복구할 때 사용한다.
     *
     * <p>예: {@code currentStepOrder = 4} → 현재 STEP 4 진행 중.
     * 1단계에서는 필드만 준비한다.
     */
    @Column(name = "current_step_order")
    private Integer currentStepOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_accessed_at", nullable = false)
    private LocalDateTime lastAccessedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    private StudySession(String sessionCode, LocalDateTime now, long expirationDays) {
        this.sessionCode = sessionCode;
        this.status = SessionStatus.CREATED;
        this.createdAt = now;
        this.updatedAt = now;
        this.lastAccessedAt = now;
        this.expiresAt = now.plusDays(expirationDays);
    }

    /**
     * 새 학습 세션을 생성한다. 상태는 항상 {@link SessionStatus#CREATED}로 시작한다.
     *
     * @param sessionCode    서버가 발급한 8자리 코드
     * @param now            생성 시각
     * @param expirationDays 마지막 접근 이후 보관 일수
     */
    public static StudySession create(String sessionCode, LocalDateTime now, long expirationDays) {
        return new StudySession(sessionCode, now, expirationDays);
    }

    /**
     * 세션에 접근했음을 기록한다.
     *
     * <p>만료 기준은 "마지막 접근 후 N일"이므로, 접근할 때마다 보관 기한을 함께 연장한다.
     *
     * @param now            접근 시각
     * @param expirationDays 마지막 접근 이후 보관 일수
     */
    public void touch(LocalDateTime now, long expirationDays) {
        this.lastAccessedAt = now;
        this.expiresAt = now.plusDays(expirationDays);
        this.updatedAt = now;
    }

    /**
     * 시험 정보를 등록하거나 수정한다.
     *
     * <p>두 시간 값의 의미가 다르므로 계산된 결과를 각각 받는다.
     * <ul>
     *   <li>{@code availableStudyMinutes} — 사용자가 입력한 원본값. 최초 계획의 기준이 된다.</li>
     *   <li>{@code remainingStudyMinutes} — 시험까지 남은 실제 시간까지 반영한 현재 사용 가능 시간.</li>
     * </ul>
     * 실제 계산(둘 중 작은 값 선택)은 서비스가 담당한다. 엔티티는 계산에 필요한
     * 현재 시각을 알지 못하기 때문이다.
     *
     * <p>시험 정보만 바뀌었다고 해서 {@code status}를 바꾸지 않는다.
     * 상태는 파일 업로드 단계에서 {@code UPLOADING}으로 넘어간다.
     *
     * <p>TODO(STEP 6~7): 커리큘럼이 이미 생성된 뒤에 시험 정보가 바뀌면
     * 남은 StudyStep의 시간을 다시 배분해야 한다. 커리큘럼이 없는 현재 단계에서는 저장만 한다.
     */
    public void updateExamInfo(
            String subject,
            LocalDateTime examAt,
            int availableStudyMinutes,
            int remainingStudyMinutes,
            LocalDateTime now
    ) {
        this.subject = subject;
        this.examAt = examAt;
        this.availableStudyMinutes = availableStudyMinutes;
        this.remainingStudyMinutes = remainingStudyMinutes;
        this.updatedAt = now;
    }

    /** 시험 정보가 등록되었는지 여부. 이후 단계에서 업로드/분석 진입 조건으로 쓴다. */
    public boolean hasExamInfo() {
        return examAt != null && availableStudyMinutes != null;
    }

    /**
     * 강의자료 등록 단계로 넘어간다.
     *
     * <p>이 프로젝트에서 {@code UPLOADING}은 "업로드가 진행 중"이 아니라
     * "강의자료를 등록하는 단계"를 뜻한다. AI 분석을 시작하기 전까지 이 상태를 유지한다.
     *
     * <p>{@code CREATED}일 때만 전이한다. 이미 등록 단계이거나 그 이후로 진행된 세션에
     * 파일을 더 올린다고 해서 상태를 되돌리지 않는다.
     */
    public void startUploading() {
        if (status == SessionStatus.CREATED) {
            this.status = SessionStatus.UPLOADING;
        }
    }

    public boolean isAnalyzing() {
        return status == SessionStatus.ANALYZING;
    }

    public boolean isReady() {
        return status == SessionStatus.READY;
    }

    /**
     * 남은 학습 시간을 줄인다. 늘리지는 않는다.
     *
     * <p>학습 계획을 만드는 시점에 시험까지 남은 실제 시간이 저장된 값보다 적을 수 있다.
     * 시험 정보를 입력한 뒤 시간이 흘렀기 때문이다. 그럴 때 실제 시간에 맞춰 낮춘다.
     *
     * <p>늘리지 않는 이유는 이 값이 학습 진행에 따라 줄어드는 값이기 때문이다.
     * 시간이 남았다고 되돌리면 이미 소진한 학습 시간이 되살아난다.
     *
     * @return 값이 실제로 바뀌었으면 true
     */
    public boolean reduceRemainingStudyMinutes(int minutes, LocalDateTime now) {
        if (remainingStudyMinutes == null || minutes >= remainingStudyMinutes) {
            return false;
        }
        this.remainingStudyMinutes = minutes;
        this.updatedAt = now;
        return true;
    }

    /**
     * AI 분석을 시작한다.
     *
     * <p>분석은 언제든 다시 요청할 수 있으므로 어느 상태에서든 넘어간다.
     * 이미 분석 중인 경우를 막는 것은 서비스의 책임이다.
     */
    public void startAnalyzing(LocalDateTime now) {
        this.status = SessionStatus.ANALYZING;
        this.updatedAt = now;
    }

    /** 분석에 성공해 학습을 시작할 수 있는 상태가 되었다. */
    public void markReady(LocalDateTime now) {
        this.status = SessionStatus.READY;
        this.updatedAt = now;
    }

    /**
     * 분석에 실패했다.
     *
     * <p>세션과 강의자료, 학습 맥락은 지우지 않는다. 사용자가 다시 분석을 요청할 수 있어야 한다.
     */
    public void markAnalysisFailed(LocalDateTime now) {
        this.status = SessionStatus.ANALYSIS_FAILED;
        this.updatedAt = now;
    }

    /**
     * 학습 단계를 시작했음을 기록한다.
     *
     * <p>{@code currentStepOrder}는 "지금 진행 중인 단계"를 뜻한다. 다른 기기에서
     * 세션 코드를 입력했을 때 어느 화면을 열어야 하는지를 이 값으로 판단한다.
     *
     * <p>세션 상태는 {@code READY}일 때만 {@code IN_PROGRESS}로 넘긴다.
     * 이미 진행 중인 세션을 되돌리지 않는다.
     */
    public void startStep(int stepOrder, LocalDateTime now) {
        if (status == SessionStatus.READY) {
            this.status = SessionStatus.IN_PROGRESS;
        }
        this.currentStepOrder = stepOrder;
        this.updatedAt = now;
    }

    /**
     * 진행 중인 단계가 없어졌음을 기록한다.
     *
     * <p>단계를 완료하면 다음 단계는 아직 시작 전이다. 이때 {@code currentStepOrder}를
     * 다음 순번으로 미리 올리면 "진행 중"과 "다음 차례"가 구분되지 않는다.
     * 그래서 비워 두고, 다음 단계를 실제로 시작할 때 다시 채운다.
     *
     * <p>세션 상태는 {@code IN_PROGRESS}로 둔다. 계획을 다 마쳐도 퀴즈와 최종 복습이
     * 남아 있으므로 여기서 {@code COMPLETED}로 넘기지 않는다.
     */
    public void clearCurrentStep(LocalDateTime now) {
        this.currentStepOrder = null;
        this.updatedAt = now;
    }

    /** 시험 시각이 지났는지 여부. 시험 정보가 없으면 판단하지 않는다. */
    public boolean isExamStarted(LocalDateTime now) {
        return examAt != null && !now.isBefore(examAt);
    }

    /** 보관 기한이 지났는지 여부. 자동 삭제 스케줄러는 이후 단계에서 추가한다. */
    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }
}
