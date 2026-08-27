package com.naeil.study.curriculum.service;

import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.session.entity.StudySession;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * STEP 완료 시점의 남은 학습 시간을 계산한다.
 *
 * <p><b>단순 차감을 쓰지 않는 이유.</b> {@code remainingStudyMinutes -= actualStudyMinutes} 로
 * 계산하면 학습 사이의 휴식·자리비움 시간이 빠지지 않는다. STEP 을 완료하고 한 시간 쉬었다가
 * 다음을 시작하면, 시험까지 남은 실제 시간은 그만큼 줄었는데 남은 학습 시간은 그대로여서
 * 계획이 시험 시각을 넘기게 된다.
 *
 * <p><b>두 기준의 작은 값을 쓴다.</b>
 * <pre>
 * remainingByUserBudget = max(0, availableStudyMinutes - 완료한 실제 학습시간 합)
 * remainingUntilExam    = max(0, 지금부터 시험까지 남은 분)
 * remaining             = min(위 둘)
 * </pre>
 *
 * <p>사용자 예산 기준은 "내가 공부할 수 있다고 정한 전체 시간"을 넘지 않게 하고, 시험 기준은
 * "실제로 남은 시간"을 넘지 않게 한다. 쉰 시간은 시험 기준에서 자연히 반영된다.
 *
 * <p>시간 단위는 프로젝트 공통 정책을 따른다. {@link ChronoUnit#MINUTES} 기준으로 초 단위는
 * 버린다. 남은 시간을 올려 잡아 계획이 시험을 넘기는 것보다 내려 잡는 편이 안전하다.
 *
 * <p>주입받은 {@code Clock} 을 쓰지 않고 현재 시각을 인자로 받는다. 순수 계산으로 두어야
 * 호출부와 시간 기준이 어긋나지 않고, 시각을 고정해 단위 테스트하기 쉽다.
 */
@Component
public class StudyTimeCalculator {

    /**
     * 남은 학습 시간(분)을 계산한다. 음수를 돌려주지 않는다.
     *
     * @param session        학습 세션. {@code availableStudyMinutes} 와 {@code examAt} 를 기준으로 쓴다
     * @param completedSteps 완료한 단계들. 실제 학습시간의 합을 낸다
     * @param now            현재 시각
     */
    public int calculateRemainingMinutes(
            StudySession session, List<StudyStep> completedSteps, LocalDateTime now) {
        int remaining = remainingByUserBudget(session, completedSteps);

        if (session.getExamAt() != null) {
            long minutesUntilExam = ChronoUnit.MINUTES.between(now, session.getExamAt());
            remaining = (int) Math.min(remaining, Math.max(minutesUntilExam, 0));
        }
        return Math.max(0, remaining);
    }

    /**
     * 사용자가 설정한 전체 학습 가능 시간에서 지금까지 실제로 쓴 시간을 뺀다.
     *
     * <p>{@code availableStudyMinutes} 가 없으면(시험 정보 이전) 예산 제약을 두지 않는다.
     * 다만 이 계산은 계획이 있는 세션에서만 호출되므로 실제로는 항상 값이 있다.
     */
    private int remainingByUserBudget(StudySession session, List<StudyStep> completedSteps) {
        Integer available = session.getAvailableStudyMinutes();
        if (available == null) {
            return Integer.MAX_VALUE;
        }
        int totalActual = completedSteps.stream()
                .map(StudyStep::getActualStudyMinutes)
                .filter(minutes -> minutes != null)
                .mapToInt(Integer::intValue)
                .sum();
        return Math.max(0, available - totalActual);
    }
}
