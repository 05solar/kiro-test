package com.naeil.study.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.session.entity.StudySession;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StudyTimeCalculator - 남은 학습 시간 재계산")
class StudyTimeCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 18, 0, 0);

    private final StudyTimeCalculator calculator = new StudyTimeCalculator();

    /** available 과 examAt 만 세팅한 세션을 만든다. */
    private StudySession session(int availableMinutes, LocalDateTime examAt) {
        StudySession session = StudySession.create("7K2M9QXF", NOW.minusHours(5), 30L);
        session.updateExamInfo("운영체제", examAt, availableMinutes, availableMinutes, NOW.minusHours(4));
        return session;
    }

    /** 실제 학습시간만 채운 완료 단계를 만든다. 계산기는 이 값만 읽는다. */
    private StudyStep completed(int actualStudyMinutes) {
        StudyStep step = StudyStep.study(null, null, 1, "단계", 40, 40, false, List.of(), NOW);
        try {
            Field field = StudyStep.class.getDeclaredField("actualStudyMinutes");
            field.setAccessible(true);
            field.set(step, actualStudyMinutes);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return step;
    }

    @Test
    @DisplayName("예산 기준과 시험 기준 중 작은 값을 남은 시간으로 쓴다")
    void takesMinimumOfBudgetAndExam() {
        StudySession session = session(240, NOW.plusHours(3));

        int remaining = calculator.calculateRemainingMinutes(
                session, List.of(completed(60)), NOW);

        // remainingByUserBudget = 240 - 60 = 180, remainingUntilExam = 180
        assertThat(remaining).isEqualTo(180);
    }

    @Test
    @DisplayName("쉰 시간은 시험 기준에서 자동으로 빠진다")
    void restIsReflectedByExamTime() {
        // 예산으로는 240분이 남지만, 시험까지 실제로 180분밖에 없다
        StudySession session = session(300, NOW.plusHours(3));

        int remaining = calculator.calculateRemainingMinutes(
                session, List.of(completed(60)), NOW);

        // remainingByUserBudget = 300 - 60 = 240, remainingUntilExam = 180 → 180
        assertThat(remaining).isEqualTo(180);
    }

    @Test
    @DisplayName("시험까지 시간이 충분하면 예산 기준으로 제한한다")
    void limitedByBudgetWhenExamIsFar() {
        StudySession session = session(180, NOW.plusMinutes(500));

        int remaining = calculator.calculateRemainingMinutes(
                session, List.of(completed(30)), NOW);

        // remainingByUserBudget = 180 - 30 = 150, remainingUntilExam = 500 → 150
        assertThat(remaining).isEqualTo(150);
    }

    @Test
    @DisplayName("실제 학습시간이 예산을 넘으면 0으로 막는다")
    void neverNegativeBudget() {
        StudySession session = session(60, NOW.plusHours(10));

        int remaining = calculator.calculateRemainingMinutes(
                session, List.of(completed(80)), NOW);

        assertThat(remaining).isZero();
    }

    @Test
    @DisplayName("시험 시각이 지났으면 남은 시간은 0이다")
    void zeroWhenExamPassed() {
        StudySession session = session(300, NOW.minusMinutes(1));

        int remaining = calculator.calculateRemainingMinutes(
                session, List.of(completed(10)), NOW);

        assertThat(remaining).isZero();
    }

    @Test
    @DisplayName("여러 완료 단계의 실제 학습시간을 합산한다")
    void sumsActualMinutesAcrossSteps() {
        StudySession session = session(300, NOW.plusHours(10));

        int remaining = calculator.calculateRemainingMinutes(
                session, List.of(completed(50), completed(40)), NOW);

        // 300 - (50 + 40) = 210, 시험까지 600분 → 210
        assertThat(remaining).isEqualTo(210);
    }

    @Test
    @DisplayName("시험까지 남은 분은 초 단위를 버린다")
    void floorsSecondsForExamTime() {
        StudySession session = session(1000, NOW.plusHours(3));

        int remaining = calculator.calculateRemainingMinutes(
                session, List.of(), NOW.plusSeconds(40));

        // 18:00:40 → 21:00:00 = 179분 20초 → 179분
        assertThat(remaining).isEqualTo(179);
    }
}
