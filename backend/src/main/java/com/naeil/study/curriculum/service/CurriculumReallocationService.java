package com.naeil.study.curriculum.service;

import com.naeil.study.curriculum.entity.Curriculum;
import com.naeil.study.curriculum.entity.SkipReason;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.entity.StudyStepStatus;
import com.naeil.study.curriculum.planner.CurriculumPlanner;
import com.naeil.study.curriculum.planner.ReallocatedStep;
import com.naeil.study.curriculum.planner.ReallocationCandidate;
import com.naeil.study.curriculum.planner.ReallocationResult;
import com.naeil.study.session.entity.StudySession;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * STEP 완료 후 남은 시간을 다시 계산하고 남은 {@code PENDING} 단계의 배정 시간을 재조정한다.
 *
 * <p>이 서비스가 하는 일은 세 가지다.
 * <pre>
 * 1. 남은 학습 시간 재계산   {@link StudyTimeCalculator}
 * 2. PENDING 단계 재배분      {@link CurriculumPlanner#reallocate}
 * 3. 결과를 엔티티에 반영      세션 남은 시간 / 단계 배정·상태 / 계획 총 배정 시간
 * </pre>
 *
 * <p><b>완료·진행 중 단계는 건드리지 않는다.</b> 재배분 대상은 {@code PENDING} 뿐이다.
 * 완료한 단계의 배정·실제·권장 시간, 진행 중 단계의 값은 그대로 둔다.
 *
 * <p>트랜잭션 경계를 따로 두지 않는다. STEP 완료와 한 흐름이어야 하므로 호출부
 * ({@link StudyStepService#complete})의 트랜잭션 안에서 실행된다. 외부 I/O 가 없어 그럴 수 있다.
 */
@Service
public class CurriculumReallocationService {

    private static final Logger log = LoggerFactory.getLogger(CurriculumReallocationService.class);

    private final StudyTimeCalculator studyTimeCalculator;
    private final CurriculumPlanner curriculumPlanner;

    public CurriculumReallocationService(
            StudyTimeCalculator studyTimeCalculator,
            CurriculumPlanner curriculumPlanner
    ) {
        this.studyTimeCalculator = studyTimeCalculator;
        this.curriculumPlanner = curriculumPlanner;
    }

    /**
     * 재조정 결과. 응답에서 "무엇이 어떻게 바뀌었는지" 보여줄 수 있도록 이전 값을 함께 담는다.
     *
     * @param remainingStudyMinutes 재계산한 남은 학습 시간
     * @param changed               배정 시간 또는 상태가 하나라도 바뀌었으면 true
     * @param steps                 재조정 대상이던 {@code PENDING} 단계들의 변화 (원래 순서)
     */
    public record ReallocationOutcome(
            int remainingStudyMinutes,
            boolean changed,
            List<StepChange> steps
    ) {
    }

    /**
     * 단계 하나의 변화.
     *
     * @param previousAllocatedMinutes 재조정 전 배정 시간
     * @param allocatedMinutes         재조정 후 배정 시간 (SKIPPED 는 0)
     * @param status                   재조정 후 상태 (PENDING 또는 SKIPPED)
     */
    public record StepChange(
            UUID stepId,
            int previousAllocatedMinutes,
            int allocatedMinutes,
            StudyStepStatus status
    ) {
    }

    /**
     * 남은 시간을 재계산하고 {@code PENDING} 단계를 재배분한다.
     *
     * @param session  학습 세션. 남은 학습 시간을 갱신한다
     * @param curriculum 학습 계획. 총 배정 시간을 갱신한다
     * @param allSteps 계획의 모든 단계 (완료·진행 중·남은 단계 포함). 이미 로딩되어 있어야 한다
     * @param now      현재 시각
     */
    public ReallocationOutcome reallocate(
            StudySession session, Curriculum curriculum, List<StudyStep> allSteps, LocalDateTime now) {

        List<StudyStep> completed = allSteps.stream().filter(StudyStep::isCompleted).toList();
        int remaining = studyTimeCalculator.calculateRemainingMinutes(session, completed, now);
        session.recalculateRemainingStudyMinutes(remaining, now);

        List<StudyStep> pending = allSteps.stream()
                .filter(StudyStep::isPending)
                .sorted(Comparator.comparingInt(StudyStep::getStepOrder))
                .toList();

        Map<UUID, StudyStep> pendingById = new LinkedHashMap<>();
        pending.forEach(step -> pendingById.put(step.getId(), step));

        ReallocationResult result = curriculumPlanner.reallocate(
                remaining, pending.stream().map(ReallocationCandidate::from).toList());

        boolean changed = false;
        List<StepChange> changes = new ArrayList<>(result.steps().size());
        for (ReallocatedStep reallocated : result.steps()) {
            StudyStep step = pendingById.get(reallocated.stepId());
            int previous = step.getAllocatedMinutes();

            if (reallocated.status() == StudyStepStatus.SKIPPED) {
                step.skip(SkipReason.TIME_CONSTRAINT, now);
            } else {
                step.reallocate(reallocated.allocatedMinutes(), now);
            }

            boolean stepChanged = previous != step.getAllocatedMinutes()
                    || reallocated.status() != StudyStepStatus.PENDING;
            changed = changed || stepChanged;
            changes.add(new StepChange(
                    step.getId(), previous, step.getAllocatedMinutes(), step.getStatus()));
        }

        curriculum.updateTotalAllocatedMinutes(totalAllocatedMinutes(allSteps), now);

        if (changed) {
            log.info("curriculum reallocated: sessionId={}, remaining={}min, steps={}, before={}, after={}",
                    session.getId(), remaining, changes.size(),
                    changes.stream().map(StepChange::previousAllocatedMinutes).toList(),
                    changes.stream().map(StepChange::allocatedMinutes).toList());
        }
        return new ReallocationOutcome(remaining, changed, List.copyOf(changes));
    }

    /**
     * 현재 활성 계획의 총 배정 시간. 완료·진행 중·남은 단계의 배정 시간을 더한다.
     * SKIPPED 단계는 배정 시간이 0이라 자연히 빠진다.
     */
    private int totalAllocatedMinutes(List<StudyStep> allSteps) {
        return allSteps.stream().mapToInt(StudyStep::getAllocatedMinutes).sum();
    }
}
