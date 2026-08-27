package com.naeil.study.quiz.entity;

import com.naeil.study.session.entity.StudySession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 세션이 한 문제에 낸 답안과 채점 결과.
 *
 * <p><b>불변이다.</b> 첫 답안이 곧 최종 답안이다({@code UNIQUE(session_id, quiz_id)}).
 * 같은 문제에 다시 답하면 기존 결과를 그대로 돌려준다. 이 값이 이후 학습 성취도 판단의
 * 근거가 되므로, 정답을 본 뒤 답을 바꾸는 경로를 만들지 않는다.
 *
 * <p>세션을 함께 저장하는 이유: 문제는 Topic(세션 소유)에 속하지만, 답안 조회와 집계는
 * 항상 세션 기준으로 하며 다른 세션의 결과와 섞이면 안 되기 때문이다.
 *
 * <p>{@code isCorrect} 는 서버가 {@code selectedIndex == correctIndex} 로 계산한다.
 * 클라이언트가 보낸 판정을 믿지 않는다.
 */
@Entity
@Table(
        name = "quiz_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_quiz_results_session_id_quiz_id", columnNames = {"session_id", "quiz_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizResult {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private StudySession studySession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false, updatable = false)
    private Quiz quiz;

    /** 사용자가 선택한 보기의 인덱스. 0~3. */
    @Column(name = "selected_index", nullable = false, updatable = false)
    private int selectedIndex;

    @Column(name = "is_correct", nullable = false, updatable = false)
    private boolean correct;

    /** 답안을 제출한 시각. 서버 시각이다. */
    @Column(name = "answered_at", nullable = false, updatable = false)
    private LocalDateTime answeredAt;

    private QuizResult(
            StudySession studySession,
            Quiz quiz,
            int selectedIndex,
            boolean correct,
            LocalDateTime answeredAt
    ) {
        this.studySession = studySession;
        this.quiz = quiz;
        this.selectedIndex = selectedIndex;
        this.correct = correct;
        this.answeredAt = answeredAt;
    }

    /**
     * 채점을 마친 답안을 기록한다.
     *
     * <p>정답 여부는 호출 전에 {@link Quiz#isCorrectAnswer(int)} 로 계산해 넘긴다.
     */
    public static QuizResult create(
            StudySession studySession,
            Quiz quiz,
            int selectedIndex,
            boolean correct,
            LocalDateTime answeredAt
    ) {
        return new QuizResult(studySession, quiz, selectedIndex, correct, answeredAt);
    }
}
