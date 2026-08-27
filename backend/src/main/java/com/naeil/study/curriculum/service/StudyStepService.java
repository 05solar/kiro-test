package com.naeil.study.curriculum.service;

import com.naeil.study.curriculum.entity.Curriculum;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.entity.StudyStepStatus;
import com.naeil.study.curriculum.exception.AnotherStepInProgressException;
import com.naeil.study.curriculum.exception.CurriculumNotFoundException;
import com.naeil.study.curriculum.exception.ExamAlreadyStartedException;
import com.naeil.study.curriculum.exception.InvalidStudyStepOrderException;
import com.naeil.study.curriculum.exception.StudyStepAlreadyCompletedException;
import com.naeil.study.curriculum.exception.StudyStepNotFoundException;
import com.naeil.study.curriculum.exception.StudyStepNotStartedException;
import com.naeil.study.curriculum.repository.CurriculumRepository;
import com.naeil.study.curriculum.repository.StudyStepRepository;
import com.naeil.study.curriculum.service.CurriculumReallocationService.ReallocationOutcome;
import com.naeil.study.curriculum.service.CurriculumReallocationService.StepChange;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습 단계의 실제 진행. 시작 / 완료 / 실제 학습시간 기록을 담당한다.
 *
 * <p>계획을 <b>만드는</b> 책임({@link CurriculumService})과 계획을 <b>수행하는</b> 책임을 나눈다.
 * 한쪽은 세션당 한 번 일어나고 다른 쪽은 단계 수만큼 반복된다. 다루는 시간 값도 다르다.
 *
 * <p><b>단계를 완료하면 남은 계획을 동적으로 재조정한다(9단계).</b> 실제 학습시간을 기록한 뒤,
 * 현재 시각과 시험 시각·사용자 예산을 함께 보고 남은 학습 시간을 다시 계산하고
 * ({@link StudyTimeCalculator}) 남은 {@code PENDING} 단계의 배정 시간을 재배분한다
 * ({@link CurriculumReallocationService}). 실제 재계산·재배분 규칙은 그 두 협력자에 있다.
 * 여기서는 순서만 지킨다 — <b>재배분을 먼저 하고 그다음에 다음 단계를 조회한다.</b> 순서가 바뀌면
 * 방금 시간 부족으로 SKIPPED 된 단계를 다음 단계로 잘못 알려 줄 수 있다.
 *
 * <p>단계 상태와 세션 / 계획 상태가 함께 바뀌므로 한 트랜잭션으로 처리한다.
 */
@Service
@Transactional(readOnly = true)
public class StudyStepService {

    private static final Logger log = LoggerFactory.getLogger(StudyStepService.class);

    private final CurriculumRepository curriculumRepository;
    private final StudyStepRepository studyStepRepository;
    private final SessionService sessionService;
    private final CurriculumReallocationService reallocationService;
    private final Clock clock;

    public StudyStepService(
            CurriculumRepository curriculumRepository,
            StudyStepRepository studyStepRepository,
            SessionService sessionService,
            CurriculumReallocationService reallocationService,
            Clock clock
    ) {
        this.curriculumRepository = curriculumRepository;
        this.studyStepRepository = studyStepRepository;
        this.sessionService = sessionService;
        this.reallocationService = reallocationService;
        this.clock = clock;
    }

    /**
     * 단계 완료 결과.
     *
     * @param nextStep              다음에 시작할 수 있는 단계. 재조정까지 반영한 뒤의 첫 {@code PENDING}
     * @param curriculumCompleted   수행 가능한 {@code PENDING} 단계가 없으면 true
     * @param remainingStudyMinutes 재계산한 남은 학습 시간
     * @param reallocation          남은 단계 재조정 결과 (변화 없음도 포함)
     */
    public record CompletionResult(
            StudyStep completedStep,
            Optional<StudyStep> nextStep,
            boolean curriculumCompleted,
            int remainingStudyMinutes,
            ReallocationOutcome reallocation
    ) {
    }

    /**
     * 학습 단계를 시작한다.
     *
     * <p>검증 순서
     * <pre>
     * 세션 → 계획 → 단계 소유 → 완료 여부 → (진행 중이면 그대로 반환)
     *   → 시험 시각 → 다른 진행 중 단계 → 순서
     * </pre>
     *
     * <p>이미 진행 중인 같은 단계에 다시 시작을 요청하면 <b>그대로 돌려준다.</b>
     * 버튼을 두 번 눌렀거나 요청이 재전송된 경우인데, 여기서 {@code startedAt}을 현재 시각으로
     * 덮으면 그동안 공부한 시간이 사라진다.
     *
     * @throws StudyStepNotFoundException          없거나 다른 세션의 단계
     * @throws StudyStepAlreadyCompletedException  이미 완료한 단계
     * @throws ExamAlreadyStartedException         시험 시각이 지남
     * @throws AnotherStepInProgressException      다른 단계가 진행 중
     * @throws InvalidStudyStepOrderException      앞선 단계를 건너뜀
     */
    @Transactional
    public StudyStep start(String sessionCode, UUID stepId) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        Curriculum curriculum = findCurriculum(session);
        StudyStep step = findStep(curriculum, stepId);

        if (step.isCompleted()) {
            throw new StudyStepAlreadyCompletedException();
        }
        if (step.isInProgress()) {
            return step;
        }

        LocalDateTime now = now();
        if (session.isExamStarted(now)) {
            throw new ExamAlreadyStartedException();
        }
        ensureNoStepInProgress(curriculum);
        ensureStartable(curriculum, step);

        step.start(now);
        session.startStep(step.getStepOrder(), now);
        curriculum.startProgress(now);

        log.info("study step started: sessionId={}, stepOrder={}, allocated={}min",
                session.getId(), step.getStepOrder(), step.getAllocatedMinutes());
        return step;
    }

    /**
     * 학습 단계를 완료하고 실제 학습시간을 기록한다.
     *
     * <p>이미 완료한 단계에 다시 요청이 오면 <b>기존 결과를 그대로 돌려준다.</b>
     * 다시 계산하면 완료 시각과 실제 학습시간이 요청할 때마다 늘어나고, 재조정도 매번 다시 돌아
     * 남은 단계 배정이 흔들린다. 재조정은 완료할 때 한 번만 한다.
     *
     * <p>시험 시각이 지났더라도 완료는 막지 않는다. 이미 학습한 기록이 존재하기 때문이다.
     * 이 경우 재계산된 남은 시간이 0이 되어 남은 단계가 모두 SKIPPED 될 수 있다.
     *
     * <p><b>처리 순서</b>
     * <pre>
     * 완료 기록 → 남은 시간 재계산 → PENDING 재배분 → 다음 PENDING 조회 → 계획 완료 판정
     * </pre>
     * 다음 단계를 재배분보다 먼저 찾지 않는다. 재배분으로 SKIPPED 된 단계를 다음 단계로 알려 주면
     * 시작할 수 없는 단계를 화면에 띄우게 된다.
     *
     * @throws StudyStepNotFoundException   없거나 다른 세션의 단계
     * @throws StudyStepNotStartedException 시작하지 않은 단계
     */
    @Transactional
    public CompletionResult complete(String sessionCode, UUID stepId) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        Curriculum curriculum = findCurriculum(session);
        StudyStep step = findStep(curriculum, stepId);

        if (step.isCompleted()) {
            return alreadyCompletedResult(session, curriculum, step);
        }
        if (!step.isInProgress()) {
            throw new StudyStepNotStartedException();
        }

        LocalDateTime now = now();
        step.complete(now);
        session.clearCurrentStep(now);
        studyStepRepository.flush();

        List<StudyStep> allSteps =
                studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(curriculum.getId());
        ReallocationOutcome reallocation =
                reallocationService.reallocate(session, curriculum, allSteps, now);

        Optional<StudyStep> next = firstPending(allSteps);
        boolean curriculumCompleted = next.isEmpty();
        if (curriculumCompleted && !curriculum.isCompleted()) {
            curriculum.completeProgress(now);
        }

        log.info("study step completed: sessionId={}, stepOrder={}, allocated={}min, actual={}min, "
                        + "remaining={}min, reallocated={}",
                session.getId(), step.getStepOrder(), step.getAllocatedMinutes(),
                step.getActualStudyMinutes(), reallocation.remainingStudyMinutes(),
                reallocation.changed());
        return new CompletionResult(
                step, next, curriculumCompleted, reallocation.remainingStudyMinutes(), reallocation);
    }

    /**
     * 지금 시작할 수 있는 단계인지 확인한다.
     *
     * <p>시작 가능한 단계는 {@code PENDING} 중 순번이 가장 앞선 하나뿐이다.
     * 계획은 중요도 순으로 배치되어 있어서, 뒤 단계부터 하면 시간이 모자랄 때
     * 정작 중요한 단계가 남는다.
     */
    private void ensureStartable(Curriculum curriculum, StudyStep step) {
        StudyStep startable = studyStepRepository
                .findFirstByCurriculumIdAndStatusOrderByStepOrderAsc(
                        curriculum.getId(), StudyStepStatus.PENDING)
                .orElseThrow(InvalidStudyStepOrderException::new);
        if (!startable.getId().equals(step.getId())) {
            throw new InvalidStudyStepOrderException();
        }
    }

    /**
     * 진행 중인 다른 단계가 없는지 확인한다.
     *
     * <p>노트북에서 STEP 2를 시작한 채로 휴대폰에서 STEP 3을 시작하면, 같은 시간이
     * 두 단계의 실제 학습시간에 동시에 기록된다. 한 계획에서 진행 중인 단계는 하나뿐이다.
     */
    private void ensureNoStepInProgress(Curriculum curriculum) {
        studyStepRepository
                .findFirstByCurriculumIdAndStatusOrderByStepOrderAsc(
                        curriculum.getId(), StudyStepStatus.IN_PROGRESS)
                .ifPresent(inProgress -> {
                    throw new AnotherStepInProgressException();
                });
    }

    /**
     * 이미 완료한 단계에 다시 요청이 온 경우의 결과. <b>아무것도 바꾸지 않는다.</b>
     *
     * <p>재조정은 완료 시점에 이미 한 번 돌았다. 여기서 다시 계산하면 그동안 흐른 시간 때문에
     * 남은 시간과 배정이 요청할 때마다 달라진다. 그래서 저장된 현재 상태를 그대로 읽어 돌려준다.
     */
    private CompletionResult alreadyCompletedResult(
            StudySession session, Curriculum curriculum, StudyStep completedStep) {
        List<StudyStep> allSteps =
                studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(curriculum.getId());
        Optional<StudyStep> next = firstPending(allSteps);
        int remaining = session.getRemainingStudyMinutes() == null
                ? 0 : session.getRemainingStudyMinutes();

        List<StepChange> unchanged = allSteps.stream()
                .filter(StudyStep::isPending)
                .map(step -> new StepChange(step.getId(), step.getAllocatedMinutes(),
                        step.getAllocatedMinutes(), step.getStatus()))
                .toList();
        ReallocationOutcome reallocation = new ReallocationOutcome(remaining, false, unchanged);
        return new CompletionResult(completedStep, next, next.isEmpty(), remaining, reallocation);
    }

    private Optional<StudyStep> firstPending(List<StudyStep> steps) {
        return steps.stream()
                .filter(StudyStep::isPending)
                .min(Comparator.comparingInt(StudyStep::getStepOrder));
    }

    private Curriculum findCurriculum(StudySession session) {
        return curriculumRepository.findByStudySessionId(session.getId())
                .orElseThrow(CurriculumNotFoundException::new);
    }

    /**
     * 세션의 계획에 속한 단계를 찾는다.
     *
     * <p>단계 id만으로 찾지 않는다. 다른 세션의 단계 id를 넣어도 조회되지 않아야 한다.
     * 이때 403이 아니라 404를 주는 이유는, 403이면 "그 단계는 존재한다"는 사실이 드러나기
     * 때문이다.
     */
    private StudyStep findStep(Curriculum curriculum, UUID stepId) {
        return studyStepRepository.findByIdAndCurriculumId(stepId, curriculum.getId())
                .orElseThrow(StudyStepNotFoundException::new);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
