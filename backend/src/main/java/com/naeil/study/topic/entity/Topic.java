package com.naeil.study.topic.entity;

import com.naeil.study.session.entity.StudySession;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AI가 강의자료를 분석해 만든 학습 단위.
 *
 * <p>강의자료를 사람이 공부할 수 있는 덩어리로 나눈 결과다.
 * 파일 단위가 아니라 개념 단위이므로, 여러 문서에 걸친 내용이 하나의 Topic이 될 수 있다.
 *
 * <p><b>여기 담긴 시간은 최종 학습 계획이 아니다.</b>
 * {@link #estimatedStudyMinutes}는 "이 주제를 제대로 학습하는 데 필요한 시간"이며,
 * 사용자의 남은 학습 시간에 맞춘 조정은 다음 커리큘럼 단계에서 한다.
 * 그래서 전체 합이 {@code remainingStudyMinutes}를 넘어도 정상이다.
 *
 * <p>학습 맥락 일치 여부(4개 boolean)는 커리큘럼 단계에서 시간 배분과 삭제 여부를 정하는 데 쓴다.
 */
@Entity
@Table(name = "topics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Topic {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private StudySession studySession;

    /** 주제 이름. 문장이 아니라 이름이어야 한다. */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 시험 직전 이해해야 할 핵심 내용 요약. 강의자료에 없는 사실을 담지 않는다. */
    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    /**
     * 핵심 개념 목록.
     *
     * <p>{@code columnDefinition} 을 적지 않는다. Dialect가 알아서 고른다
     * (PostgreSQL은 jsonb, H2는 json). 직접 적으면 한쪽 DB에서 DDL이 깨진다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_points", nullable = false)
    private List<String> keyPoints;

    /** 학습 우선순위. 시험 출제 확률이 아니다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "importance", nullable = false, length = 20)
    private TopicImportance importance;

    /** 이 주제를 학습하는 데 필요한 예상 시간(분). 남은 시간에 맞춘 조정 전 값이다. */
    @Column(name = "estimated_study_minutes", nullable = false)
    private int estimatedStudyMinutes;

    /** 교수님이 강조했다고 사용자가 밝힌 내용과 관련 있는가. */
    @Column(name = "professor_emphasis_matched", nullable = false)
    private boolean professorEmphasisMatched;

    /** 기출/예상 문제와 관련 있는가. */
    @Column(name = "past_exam_matched", nullable = false)
    private boolean pastExamMatched;

    /** 사용자가 자신 없다고 밝힌 범위와 관련 있는가. */
    @Column(name = "weak_area_matched", nullable = false)
    private boolean weakAreaMatched;

    /**
     * 사용자가 반드시 공부하겠다고 밝힌 범위와 관련 있는가.
     *
     * <p>다른 세 값과 성격이 다르다. 우선순위 가중치가 아니라 <b>제약</b>이다.
     * 커리큘럼 단계에서 시간이 부족해도 이 Topic은 가능한 한 남긴다.
     */
    @Column(name = "must_study_matched", nullable = false)
    private boolean mustStudyMatched;

    /**
     * 이 Topic이 어느 강의자료에서 나왔는지.
     *
     * <p>AI에게는 {@code DOC_1} 같은 참조값만 주고, 서버가 실제 문서 UUID로 바꿔 저장한다.
     * AI가 임의의 UUID를 만들어 내지 못하게 하기 위해서다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_document_ids", nullable = false)
    private List<UUID> sourceDocumentIds;

    /**
     * 기본 학습 순서. 개념 사이의 의존 관계를 반영한다.
     *
     * <p>최종 커리큘럼 순서가 아니다. 다음 단계에서 중요도와 남은 시간을 함께 보고 다시 정한다.
     */
    @Column(name = "topic_order", nullable = false)
    private int topicOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Topic(
            StudySession studySession,
            String title,
            String summary,
            List<String> keyPoints,
            TopicImportance importance,
            int estimatedStudyMinutes,
            boolean professorEmphasisMatched,
            boolean pastExamMatched,
            boolean weakAreaMatched,
            boolean mustStudyMatched,
            List<UUID> sourceDocumentIds,
            int topicOrder,
            LocalDateTime now
    ) {
        this.studySession = studySession;
        this.title = title;
        this.summary = summary;
        this.keyPoints = keyPoints;
        this.importance = importance;
        this.estimatedStudyMinutes = estimatedStudyMinutes;
        this.professorEmphasisMatched = professorEmphasisMatched;
        this.pastExamMatched = pastExamMatched;
        this.weakAreaMatched = weakAreaMatched;
        this.mustStudyMatched = mustStudyMatched;
        this.sourceDocumentIds = sourceDocumentIds;
        this.topicOrder = topicOrder;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 검증을 마친 분석 결과로 Topic을 만든다.
     *
     * <p>AI 응답을 그대로 받지 않는다. 값 범위와 문서 참조는 이미 서버에서 확인한 뒤여야 한다.
     */
    public static Topic create(
            StudySession studySession,
            String title,
            String summary,
            List<String> keyPoints,
            TopicImportance importance,
            int estimatedStudyMinutes,
            boolean professorEmphasisMatched,
            boolean pastExamMatched,
            boolean weakAreaMatched,
            boolean mustStudyMatched,
            List<UUID> sourceDocumentIds,
            int topicOrder,
            LocalDateTime now
    ) {
        return new Topic(studySession, title, summary, keyPoints, importance, estimatedStudyMinutes,
                professorEmphasisMatched, pastExamMatched, weakAreaMatched, mustStudyMatched,
                sourceDocumentIds, topicOrder, now);
    }
}
