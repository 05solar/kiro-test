package com.naeil.study.curriculum.service;

import com.naeil.study.curriculum.entity.Curriculum;
import com.naeil.study.curriculum.entity.StudyStep;
import com.naeil.study.curriculum.exception.CurriculumNotFoundException;
import com.naeil.study.curriculum.exception.NoStudyTimeAvailableException;
import com.naeil.study.curriculum.exception.SessionNotReadyException;
import com.naeil.study.curriculum.exception.TopicsRequiredException;
import com.naeil.study.curriculum.planner.CurriculumPlan;
import com.naeil.study.curriculum.planner.CurriculumPlanner;
import com.naeil.study.curriculum.planner.PlannedStep;
import com.naeil.study.curriculum.planner.PlanningTopic;
import com.naeil.study.curriculum.repository.CurriculumRepository;
import com.naeil.study.curriculum.repository.StudyStepRepository;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.topic.entity.Topic;
import com.naeil.study.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최초 학습 계획 생성 / 조회.
 *
 * <p>AI를 부르지 않는다. 6단계에서 만들어진 Topic을 기준 데이터로 쓴다.
 * 계획을 만들자고 강의자료 전체를 다시 분석에 보내지 않는다.
 *
 * <p>외부 호출이 없어 한 트랜잭션으로 처리한다. 분석 단계처럼 트랜잭션을 쪼갤 이유가 없다.
 */
@Service
@Transactional(readOnly = true)
public class CurriculumService {

    private static final Logger log = LoggerFactory.getLogger(CurriculumService.class);

    private final CurriculumRepository curriculumRepository;
    private final StudyStepRepository studyStepRepository;
    private final TopicRepository topicRepository;
    private final SessionService sessionService;
    private final CurriculumPlanner curriculumPlanner;
    private final Clock clock;

    public CurriculumService(
            CurriculumRepository curriculumRepository,
            StudyStepRepository studyStepRepository,
            TopicRepository topicRepository,
            SessionService sessionService,
            CurriculumPlanner curriculumPlanner,
            Clock clock
    ) {
        this.curriculumRepository = curriculumRepository;
        this.studyStepRepository = studyStepRepository;
        this.topicRepository = topicRepository;
        this.sessionService = sessionService;
        this.curriculumPlanner = curriculumPlanner;
        this.clock = clock;
    }

    /**
     * 계획 생성 결과.
     *
     * @param created 이번 요청으로 새로 만들었으면 true, 기존 계획을 돌려줬으면 false
     */
    public record CurriculumResult(Curriculum curriculum, List<StudyStep> steps, boolean created) {
    }

    /**
     * 최초 학습 계획을 만든다.
     *
     * <p>이미 계획이 있으면 <b>다시 만들지 않고 기존 것을 돌려준다.</b> 같은 요청을 두 번 보내도
     * 결과가 같아야 하고, 사용자가 이미 본 계획이 새로고침 한 번에 바뀌면 안 된다.
     * 계획을 다시 세우는 기능은 별도로 만든다.
     *
     * @throws SessionNotReadyException        분석이 끝나지 않은 세션
     * @throws TopicsRequiredException         분석된 주제가 없음
     * @throws NoStudyTimeAvailableException   남은 학습 시간이 없음
     * @throws com.naeil.study.curriculum.exception.CurriculumGenerationFailedException 계획 생성 실패
     */
    @Transactional
    public CurriculumResult create(String sessionCode) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        UUID sessionId = session.getId();

        Curriculum existing = curriculumRepository.findByStudySessionId(sessionId).orElse(null);
        if (existing != null) {
            return new CurriculumResult(existing, findSteps(existing), false);
        }

        if (!session.isReady()) {
            throw new SessionNotReadyException();
        }
        List<Topic> topics = topicRepository.findAllByStudySessionIdOrderByTopicOrderAsc(sessionId);
        if (topics.isEmpty()) {
            throw new TopicsRequiredException();
        }

        LocalDateTime now = now();
        int effectiveMinutes = resolveEffectiveMinutes(session, now);

        CurriculumPlan plan = curriculumPlanner.planInitial(
                effectiveMinutes, topics.stream().map(PlanningTopic::from).toList());

        Curriculum curriculum = curriculumRepository.save(
                Curriculum.create(session, effectiveMinutes, plan.totalAllocatedMinutes(), now));
        List<StudyStep> steps = studyStepRepository.saveAllAndFlush(
                toStudySteps(curriculum, plan, topics, now));

        log.info("curriculum created: sessionId={}, available={}, allocated={}, steps={}",
                sessionId, effectiveMinutes, plan.totalAllocatedMinutes(), steps.size());
        return new CurriculumResult(curriculum, steps, true);
    }

    /**
     * 학습 계획을 조회한다.
     *
     * @throws CurriculumNotFoundException 아직 계획을 만들지 않은 경우
     */
    @Transactional
    public CurriculumResult find(String sessionCode) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        Curriculum curriculum = curriculumRepository.findByStudySessionId(session.getId())
                .orElseThrow(CurriculumNotFoundException::new);
        return new CurriculumResult(curriculum, findSteps(curriculum), false);
    }

    /**
     * 계획을 세울 때 실제로 쓸 수 있는 시간을 정한다.
     *
     * <pre>
     * effectiveMinutes = min(세션의 남은 학습 시간, 지금부터 시험까지 남은 분)
     * </pre>
     *
     * <p>시험 정보를 입력한 뒤 시간이 흘렀을 수 있다. 저장된 값이 300분이어도 시험까지
     * 220분밖에 안 남았다면 300분짜리 계획은 실행할 수 없다.
     *
     * <p>더 짧아진 경우 세션의 값도 함께 낮춘다. 이후 단계들이 같은 기준을 보게 하기 위해서다.
     */
    private int resolveEffectiveMinutes(StudySession session, LocalDateTime now) {
        Integer remaining = session.getRemainingStudyMinutes();
        if (remaining == null || remaining <= 0) {
            throw new NoStudyTimeAvailableException();
        }

        int effective = remaining;
        if (session.getExamAt() != null) {
            long minutesUntilExam = ChronoUnit.MINUTES.between(now, session.getExamAt());
            effective = (int) Math.min(remaining, Math.max(minutesUntilExam, 0));
        }
        if (effective <= 0) {
            throw new NoStudyTimeAvailableException();
        }

        if (session.reduceRemainingStudyMinutes(effective, now)) {
            log.info("remaining study minutes synced to exam time: sessionId={}, {} -> {}",
                    session.getId(), remaining, effective);
        }
        return effective;
    }

    private List<StudyStep> toStudySteps(
            Curriculum curriculum, CurriculumPlan plan, List<Topic> topics, LocalDateTime now) {
        Map<UUID, Topic> topicsById = new LinkedHashMap<>();
        topics.forEach(topic -> topicsById.put(topic.getId(), topic));

        List<StudyStep> steps = new ArrayList<>(plan.steps().size());
        for (PlannedStep planned : plan.steps()) {
            if (planned.isReview()) {
                steps.add(StudyStep.review(curriculum, planned.order(),
                        planned.title(), planned.allocatedMinutes(), now));
                continue;
            }
            steps.add(StudyStep.study(
                    curriculum,
                    topicsById.get(planned.topicId()),
                    planned.order(),
                    planned.title(),
                    planned.allocatedMinutes(),
                    planned.originalEstimatedMinutes(),
                    planned.mandatory(),
                    planned.priorityReasons(),
                    now));
        }
        return steps;
    }

    private List<StudyStep> findSteps(Curriculum curriculum) {
        return studyStepRepository.findAllByCurriculumIdOrderByStepOrderAsc(curriculum.getId());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
