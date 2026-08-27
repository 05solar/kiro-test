package com.naeil.study.wronganswer.entity;

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
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Quiz 오답을 기반으로 AI가 만든 맞춤형 복습 요약.
 *
 * <p>세션당 현재 요약 하나만 유지한다({@code session_id} UNIQUE). 새 오답이 생겨
 * 다시 생성하면 <b>행을 교체</b>하지, 이력을 쌓지 않는다. 버전 관리는 MVP 범위 밖이다.
 *
 * <p><b>캐시 판단 기준.</b> {@code sourceLatestAnsweredAt} 은 요약을 만들 당시
 * 세션 답안들의 최신 {@code answeredAt} 이다. 지금의 최신 답안 시각과 같으면
 * 결과가 달라질 이유가 없으므로 AI를 다시 부르지 않는다.
 *
 * <p>{@code topicReviews} 는 jsonb 다. 항목 단위로 조회·수정할 일이 없어
 * 별도 테이블을 만들지 않는다.
 */
@Entity
@Table(
        name = "wrong_answer_summaries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wrong_answer_summaries_session_id", columnNames = "session_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WrongAnswerSummary {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private StudySession studySession;

    /** 생성 당시의 오답 수. */
    @Column(name = "wrong_answer_count", nullable = false)
    private int wrongAnswerCount;

    /** 전체 총평. 어떤 영역을 우선 복습할지 한두 문장으로 안내한다. */
    @Column(name = "overall_summary", nullable = false, columnDefinition = "TEXT")
    private String overallSummary;

    /** Topic 별 복습 요약. PostgreSQL 에서는 jsonb 로 저장된다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "topic_reviews", nullable = false)
    private List<TopicReviewSnapshot> topicReviews;

    /** 요약을 만들 당시 세션 답안들의 최신 answeredAt. 캐시 유효성 판단 기준이다. */
    @Column(name = "source_latest_answered_at", nullable = false)
    private LocalDateTime sourceLatestAnsweredAt;

    /** AI 요약이 실제로 생성된 시각. */
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private WrongAnswerSummary(
            StudySession studySession,
            int wrongAnswerCount,
            String overallSummary,
            List<TopicReviewSnapshot> topicReviews,
            LocalDateTime sourceLatestAnsweredAt,
            LocalDateTime now
    ) {
        this.studySession = studySession;
        this.wrongAnswerCount = wrongAnswerCount;
        this.overallSummary = overallSummary;
        this.topicReviews = topicReviews;
        this.sourceLatestAnsweredAt = sourceLatestAnsweredAt;
        this.generatedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 검증을 마친 AI 결과로 요약을 만든다. */
    public static WrongAnswerSummary create(
            StudySession studySession,
            int wrongAnswerCount,
            String overallSummary,
            List<TopicReviewSnapshot> topicReviews,
            LocalDateTime sourceLatestAnsweredAt,
            LocalDateTime now
    ) {
        return new WrongAnswerSummary(studySession, wrongAnswerCount, overallSummary,
                List.copyOf(topicReviews), sourceLatestAnsweredAt, now);
    }

    /**
     * 새 답안이 반영된 요약으로 내용을 교체한다.
     *
     * <p>새 AI 결과의 <b>검증이 끝난 뒤에만</b> 호출한다. AI 호출이 실패하면 기존 요약을
     * 그대로 남긴다. 오래된 요약이라도 없는 것보다 낫다.
     */
    public void replaceWith(
            int wrongAnswerCount,
            String overallSummary,
            List<TopicReviewSnapshot> topicReviews,
            LocalDateTime sourceLatestAnsweredAt,
            LocalDateTime now
    ) {
        this.wrongAnswerCount = wrongAnswerCount;
        this.overallSummary = overallSummary;
        this.topicReviews = List.copyOf(topicReviews);
        this.sourceLatestAnsweredAt = sourceLatestAnsweredAt;
        this.generatedAt = now;
        this.updatedAt = now;
    }

    /** 현재 답안 기준 최신 시각과 같으면 다시 만들 이유가 없다. */
    public boolean isUpToDate(LocalDateTime latestAnsweredAt) {
        return sourceLatestAnsweredAt.equals(latestAnsweredAt);
    }
}
