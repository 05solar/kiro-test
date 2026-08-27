package com.naeil.study.studycontext.service;

import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.studycontext.dto.UpdateStudyContextRequest;
import com.naeil.study.studycontext.entity.StudyContext;
import com.naeil.study.studycontext.entity.StudyContextPolicy;
import com.naeil.study.studycontext.repository.StudyContextRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습 맥락 저장 / 조회 유스케이스.
 *
 * <p>세션당 하나만 존재하므로 저장은 Upsert다. 없으면 만들고 있으면 고친다.
 * 새 행을 덧붙이지 않는다.
 *
 * <p>세션은 {@link SessionService}를 통해 가져온다. 다른 도메인의 Repository를 직접 쓰지 않는다.
 * 조회와 함께 접근시각/보관기한도 갱신되므로 학습 맥락 입력·조회 모두 세션 활동으로 기록된다.
 *
 * <p>학습 맥락 입력 여부로 세션 상태를 바꾸지 않는다. 세션 상태 머신과 독립적인 선택 정보다.
 */
@Service
@Transactional(readOnly = true)
public class StudyContextService {

    private static final Logger log = LoggerFactory.getLogger(StudyContextService.class);

    private final StudyContextRepository studyContextRepository;
    private final SessionService sessionService;
    private final Clock clock;

    public StudyContextService(
            StudyContextRepository studyContextRepository,
            SessionService sessionService,
            Clock clock
    ) {
        this.studyContextRepository = studyContextRepository;
        this.sessionService = sessionService;
        this.clock = clock;
    }

    /**
     * 학습 맥락을 저장하거나 통째로 교체한다.
     *
     * <p>입력값은 저장 전에 정규화한다. 앞뒤 공백은 지우고, 공백만 있는 값은 {@code null}이 된다.
     *
     * @throws com.naeil.study.session.exception.InvalidSessionCodeException 코드 형식 오류
     * @throws com.naeil.study.session.exception.SessionNotFoundException    세션 없음
     */
    @Transactional
    public StudyContext upsert(String sessionCode, UpdateStudyContextRequest request) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        LocalDateTime now = LocalDateTime.now(clock);

        String professorEmphasis = StudyContextPolicy.normalize(request.professorEmphasis());
        String pastExamInfo = StudyContextPolicy.normalize(request.pastExamInfo());
        String weakAreas = StudyContextPolicy.normalize(request.weakAreas());
        String mustStudyAreas = StudyContextPolicy.normalize(request.mustStudyAreas());

        StudyContext studyContext = studyContextRepository.findByStudySessionId(session.getId())
                .orElse(null);

        if (studyContext == null) {
            studyContext = studyContextRepository.save(StudyContext.create(
                    session, professorEmphasis, pastExamInfo, weakAreas, mustStudyAreas, now));
            log.info("study context created: sessionId={}", session.getId());
        } else {
            studyContext.update(professorEmphasis, pastExamInfo, weakAreas, mustStudyAreas, now);
            log.info("study context updated: sessionId={}", session.getId());
        }
        return studyContext;
    }

    /**
     * 학습 맥락을 조회한다.
     *
     * @return 아직 입력하지 않았으면 비어 있다. 이것은 오류가 아니라 정상 상태다
     * @throws com.naeil.study.session.exception.SessionNotFoundException 세션 없음
     */
    @Transactional
    public Optional<StudyContext> find(String sessionCode) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        return studyContextRepository.findByStudySessionId(session.getId());
    }
}
