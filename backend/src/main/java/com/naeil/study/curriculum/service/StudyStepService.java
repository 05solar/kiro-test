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
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import java.time.Clock;
import java.time.LocalDateTime;
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
 * <p><b>이 단계에서 하지 않는 것</b>
 * <pre>
 * remainingStudyMinutes -= actualStudyMinutes   (단순 차감)
 * 남은 단계의 allocatedMinutes 재배분
 * </pre>
 * 사용자가 브라우저를 닫아 둔 시간까지 실제 학습시간에 들어간다. 그 값을 그대로 빼면
 * 남은 학습 시간이 시험까지 남은 실제 시간과 어긋난다. 남은 시간은 현재 시각과 시험 시각을
 * 함께 보고 다시 계산해야 하며, 그것은 9단계의 일이다. 여기서는 기록만 정확히 남긴다.
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
    private final Clock clock;

    public StudyStepService(
            CurriculumRepository curriculumRepository,
            StudyStepRepository studyStepRepository,
            SessionService sessionService,
            Clock clock
    ) {
        this.curriculumRepository = curriculumRepository;
        this.studyStepRepository = studyStepRepository;
        this.sessionService = sessionService;
        this.clock = clock;
    }

    /**
     * 단계 완료 결과.
     *
     * @param nextStep            다음에 시작할 수 있는 단계. 계획을 다 마쳤으면 없다
     * @param curriculumCompleted 이 완료로 계획 전체가 끝났으면 true
     */
    public record CompletionResult(
            StudyStep completedStep,
            Optional<StudyStep> nextStep,
            boolean curriculumCompleted
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
     * 다시 계산하면 완료 시각과 실제 학습시간이 요청할 때마다 늘어난다.
     *
     * <p>시험 시각이 지났더라도 완료는 막지 않는다. 이미 학습한 기록이 존재하기 때문이다.
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
            return toCompletionResult(curriculum, step);
        }
        if (!step.isInProgress()) {
            throw new StudyStepNotStartedException();
        }

        LocalDateTime now = now();
        step.complete(now);
        session.clearCurrentStep(now);
        studyStepRepository.flush();

        CompletionResult result = toCompletionResult(curriculum, step);
        if (result.curriculumCompleted() && !curriculum.isCompleted()) {
            curriculum.completeProgress(now);
        }

        log.info("study step completed: sessionId={}, stepOrder={}, allocated={}min, actual={}min",
                session.getId(), step.getStepOrder(), step.getAllocatedMinutes(),
                step.getActualStudyMinutes());
        return result;
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

    private CompletionResult toCompletionResult(Curriculum curriculum, StudyStep completedStep) {
        Optional<StudyStep> next = studyStepRepository
                .findFirstByCurriculumIdAndStatusOrderByStepOrderAsc(
                        curriculum.getId(), StudyStepStatus.PENDING);
        return new CompletionResult(completedStep, next, next.isEmpty());
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
