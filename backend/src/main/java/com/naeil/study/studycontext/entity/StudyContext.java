package com.naeil.study.studycontext.entity;

import com.naeil.study.session.entity.StudySession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 사용자가 AI 분석에 덧붙이는 학습 맥락.
 *
 * <p>강의자료만으로는 알 수 없는 정보를 사용자에게서 직접 받는다.
 * 네 항목 모두 선택 입력이며, 하나도 입력하지 않아도 이후 기능은 정상 동작해야 한다.
 *
 * <p>세션당 최대 하나만 존재한다({@code session_id} UNIQUE).
 * 연관관계는 StudyContext → StudySession 단방향이다.
 *
 * <p><b>이 값은 사용자가 제공한 참고 정보이지 검증된 사실이 아니다.</b>
 * 이후 AI 프롬프트에서도 시스템 지시문과 명확히 분리해서 전달해야 한다.
 * 시스템 프롬프트 문자열에 이 내용을 그대로 이어붙이지 않는다.
 *
 * <p><b>필드별 향후 용도</b>
 * <pre>
 * professorEmphasis  Topic 우선순위 상향
 * pastExamInfo       Topic 우선순위 + Quiz 유형 비중 상향
 * weakAreas          학습시간 / Quiz / 복습 비중 상향
 * mustStudyAreas     가중치가 아니라 제약. 시간이 부족해도 커리큘럼에서 빼지 않는다
 * </pre>
 * 이번 단계에서는 저장과 조회만 한다. 가중치 계산은 하지 않는다.
 */
@Entity
@Table(
        name = "study_contexts",
        uniqueConstraints = @UniqueConstraint(name = "uk_study_contexts_session_id", columnNames = "session_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyContext {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private StudySession studySession;

    /** 교수님이 시험에 나온다고 강조한 부분. */
    @Column(name = "professor_emphasis", columnDefinition = "TEXT")
    private String professorEmphasis;

    /** 기출문제 또는 예상 문제. */
    @Column(name = "past_exam_info", columnDefinition = "TEXT")
    private String pastExamInfo;

    /** 사용자가 자신 없다고 밝힌 부분. */
    @Column(name = "weak_areas", columnDefinition = "TEXT")
    private String weakAreas;

    /** 반드시 공부하고 싶은 범위. 이후 커리큘럼에서 제약으로 다룬다. */
    @Column(name = "must_study_areas", columnDefinition = "TEXT")
    private String mustStudyAreas;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private StudyContext(
            StudySession studySession,
            String professorEmphasis,
            String pastExamInfo,
            String weakAreas,
            String mustStudyAreas,
            LocalDateTime now
    ) {
        this.studySession = studySession;
        this.professorEmphasis = professorEmphasis;
        this.pastExamInfo = pastExamInfo;
        this.weakAreas = weakAreas;
        this.mustStudyAreas = mustStudyAreas;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 학습 맥락을 처음 저장한다.
     *
     * @param professorEmphasis 정규화를 마친 값 (공백만 있으면 null)
     */
    public static StudyContext create(
            StudySession studySession,
            String professorEmphasis,
            String pastExamInfo,
            String weakAreas,
            String mustStudyAreas,
            LocalDateTime now
    ) {
        return new StudyContext(studySession, professorEmphasis, pastExamInfo, weakAreas, mustStudyAreas, now);
    }

    /**
     * 네 항목을 통째로 교체한다.
     *
     * <p>부분 수정이 아니라 전체 교체다. 넘어온 값이 {@code null}이면 그 항목을 비운다.
     * API가 PUT인 것과 같은 의미다.
     */
    public void update(
            String professorEmphasis,
            String pastExamInfo,
            String weakAreas,
            String mustStudyAreas,
            LocalDateTime now
    ) {
        this.professorEmphasis = professorEmphasis;
        this.pastExamInfo = pastExamInfo;
        this.weakAreas = weakAreas;
        this.mustStudyAreas = mustStudyAreas;
        this.updatedAt = now;
    }

    /** 네 항목이 모두 비어 있는지. 이후 AI 프롬프트에 맥락 절을 넣을지 판단할 때 쓴다. */
    public boolean isEmpty() {
        return professorEmphasis == null && pastExamInfo == null
                && weakAreas == null && mustStudyAreas == null;
    }
}
