package com.naeil.study.quiz.entity;

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
 * Topic 하나에 대한 4지선다 객관식 문제.
 *
 * <p>AI가 강의자료를 근거로 생성하며, 검증({@code AiQuizResponseValidator})을 통과한 값만
 * 저장된다. 저장 후에는 바뀌지 않는다. 답안 채점 결과가 이 문제를 참조하기 때문이다.
 *
 * <p><b>{@code correctIndex} 와 {@code explanation} 은 채점 전 응답에 절대 내보내지 않는다.</b>
 * 문제 목록 DTO 에는 두 값이 아예 없고, 답안 제출 응답에서만 공개한다.
 *
 * <p>{@code sourceDocumentIds} 는 이 문제의 근거가 된 강의자료다. Topic 의 출처를 그대로
 * 복사한다. AI 에게 문서 ID를 만들게 하지 않는다.
 */
@Entity
@Table(
        name = "quizzes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_quizzes_topic_id_quiz_order", columnNames = {"topic_id", "quiz_order"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz {

    /** 4지선다. 보기 수와 인덱스 범위 검증이 모두 이 값을 기준으로 한다. */
    public static final int OPTION_COUNT = 4;

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false, updatable = false)
    private Topic topic;

    /** Topic 안에서의 문제 순서. 1부터 시작한다. 조회 순서를 안정적으로 만든다. */
    @Column(name = "quiz_order", nullable = false)
    private int quizOrder;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    /** 보기 4개. PostgreSQL 에서는 jsonb 로 저장된다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", nullable = false)
    private List<String> options;

    /** 정답 보기의 배열 인덱스. 0~3. */
    @Column(name = "correct_index", nullable = false)
    private int correctIndex;

    /** 정답 해설. 강의자료 범위 안에서 작성된다. */
    @Column(name = "explanation", nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 20)
    private QuizDifficulty difficulty;

    /** 이 문제의 근거가 된 강의자료. Topic 의 출처를 복사한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_document_ids", nullable = false)
    private List<UUID> sourceDocumentIds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Quiz(
            Topic topic,
            int quizOrder,
            String question,
            List<String> options,
            int correctIndex,
            String explanation,
            QuizDifficulty difficulty,
            List<UUID> sourceDocumentIds,
            LocalDateTime now
    ) {
        this.topic = topic;
        this.quizOrder = quizOrder;
        this.question = question;
        this.options = options;
        this.correctIndex = correctIndex;
        this.explanation = explanation;
        this.difficulty = difficulty;
        this.sourceDocumentIds = sourceDocumentIds;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 검증을 마친 AI 생성 결과로 문제를 만든다.
     *
     * <p>보기 수와 정답 인덱스는 이미 검증된 뒤여야 하지만, 잘못된 값이 저장되면
     * 채점 자체가 무의미해지므로 여기서도 마지막으로 확인한다.
     */
    public static Quiz create(
            Topic topic,
            int quizOrder,
            String question,
            List<String> options,
            int correctIndex,
            String explanation,
            QuizDifficulty difficulty,
            List<UUID> sourceDocumentIds,
            LocalDateTime now
    ) {
        if (options == null || options.size() != OPTION_COUNT) {
            throw new IllegalArgumentException("quiz must have exactly " + OPTION_COUNT + " options");
        }
        if (correctIndex < 0 || correctIndex >= OPTION_COUNT) {
            throw new IllegalArgumentException("correctIndex out of range: " + correctIndex);
        }
        return new Quiz(topic, quizOrder, question, List.copyOf(options), correctIndex,
                explanation, difficulty, List.copyOf(sourceDocumentIds), now);
    }

    /** 채점. 정답 판단은 항상 서버의 이 메서드로 한다. 클라이언트 판정을 믿지 않는다. */
    public boolean isCorrectAnswer(int selectedIndex) {
        return selectedIndex == correctIndex;
    }
}
