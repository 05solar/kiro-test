package com.naeil.study.session.service;

import com.naeil.study.session.dto.UpdateExamRequest;
import com.naeil.study.session.entity.SessionCodePolicy;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.exception.InvalidExamTimeException;
import com.naeil.study.session.exception.InvalidSessionCodeException;
import com.naeil.study.session.exception.SessionCodeGenerationException;
import com.naeil.study.session.exception.SessionNotFoundException;
import com.naeil.study.session.repository.StudySessionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 생성 / 조회 유스케이스.
 *
 * <p>세션 만료 기준은 마지막 접근 후 N일이므로, 조회에 성공할 때마다
 * {@code lastAccessedAt}과 {@code expiresAt}을 함께 갱신한다.
 */
@Service
@Transactional(readOnly = true)
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final StudySessionRepository studySessionRepository;
    private final SessionCodeGenerator sessionCodeGenerator;
    private final Clock clock;
    private final long expirationDays;
    private final int maxCodeGenerationAttempts;

    public SessionService(
            StudySessionRepository studySessionRepository,
            SessionCodeGenerator sessionCodeGenerator,
            Clock clock,
            @Value("${session.expiration-days:30}") long expirationDays,
            @Value("${session.code.max-generation-attempts:10}") int maxCodeGenerationAttempts
    ) {
        this.studySessionRepository = studySessionRepository;
        this.sessionCodeGenerator = sessionCodeGenerator;
        this.clock = clock;
        this.expirationDays = expirationDays;
        this.maxCodeGenerationAttempts = maxCodeGenerationAttempts;
    }

    /**
     * 새 학습 세션을 만들고 중복되지 않는 8자리 코드를 발급한다.
     *
     * @throws SessionCodeGenerationException 정해진 횟수 안에 고유 코드를 만들지 못한 경우
     */
    @Transactional
    public StudySession createSession() {
        String sessionCode = generateUniqueSessionCode();
        StudySession session = StudySession.create(sessionCode, now(), expirationDays);
        StudySession saved = studySessionRepository.save(session);
        log.info("study session created: sessionCode={}", saved.getSessionCode());
        return saved;
    }

    /**
     * 세션 코드로 세션을 조회하고 접근 시각과 보관 기한을 갱신한다.
     *
     * @throws InvalidSessionCodeException 코드 형식이 규칙에 맞지 않는 경우 (DB 조회 없음)
     * @throws SessionNotFoundException    해당 코드의 세션이 없는 경우
     */
    @Transactional
    public StudySession getSessionAndTouch(String sessionCode) {
        StudySession session = findByCode(sessionCode);
        session.touch(now(), expirationDays);
        return session;
    }

    /**
     * 시험 정보를 등록하거나 수정한다.
     *
     * <p>처리 순서
     * <pre>
     * 코드 형식 검증 → 세션 조회 → 시험 시각 검증 → 시험까지 남은 실제 분 계산
     *   → 실제 사용 가능 시간 결정 → 저장 → 접근시각/보관기한 갱신
     * </pre>
     *
     * @throws InvalidSessionCodeException 코드 형식이 규칙에 맞지 않는 경우
     * @throws SessionNotFoundException    해당 코드의 세션이 없는 경우
     * @throws InvalidExamTimeException    시험 일시가 현재 시각보다 과거인 경우
     */
    @Transactional
    public StudySession updateExamInfo(String sessionCode, UpdateExamRequest request) {
        StudySession session = findByCode(sessionCode);
        LocalDateTime now = now();

        if (!request.examAt().isAfter(now)) {
            throw new InvalidExamTimeException();
        }

        int requestedMinutes = request.availableStudyMinutes();
        int effectiveStudyMinutes = calculateEffectiveStudyMinutes(requestedMinutes, now, request.examAt());

        session.updateExamInfo(
                request.subject().strip(),
                request.examAt(),
                requestedMinutes,
                effectiveStudyMinutes,
                now
        );
        session.touch(now, expirationDays);

        log.info("exam info updated: sessionCode={}, requested={}min, effective={}min",
                session.getSessionCode(), requestedMinutes, effectiveStudyMinutes);
        return session;
    }

    /**
     * 실제로 쓸 수 있는 학습 시간(분)을 결정한다.
     *
     * <pre>
     * effectiveStudyMinutes = min(사용자 입력 시간, 시험까지 남은 실제 시간)
     * </pre>
     *
     * <p>시간 단위는 {@link ChronoUnit#MINUTES} 기준이며 초 단위는 버린다.
     * 예: 지금이 18:00:40이고 시험이 22:00:00이면 남은 시간은 239분이다.
     * 남은 시간을 올려 잡아 계획이 시험 시각을 넘기는 것보다, 내려 잡는 편이 안전하다.
     *
     * <p>이 메서드는 시험 시각이 현재보다 미래라는 검증을 통과한 뒤에 호출한다.
     */
    private int calculateEffectiveStudyMinutes(int requestedMinutes, LocalDateTime now, LocalDateTime examAt) {
        long minutesUntilExam = ChronoUnit.MINUTES.between(now, examAt);
        return (int) Math.min(requestedMinutes, minutesUntilExam);
    }

    private StudySession findByCode(String sessionCode) {
        if (!SessionCodePolicy.isValid(sessionCode)) {
            throw new InvalidSessionCodeException();
        }
        return studySessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(SessionNotFoundException::new);
    }

    private String generateUniqueSessionCode() {
        for (int attempt = 1; attempt <= maxCodeGenerationAttempts; attempt++) {
            String candidate = sessionCodeGenerator.generate();
            if (!studySessionRepository.existsBySessionCode(candidate)) {
                return candidate;
            }
            log.warn("duplicated session code generated, retrying: attempt={}", attempt);
        }
        throw new SessionCodeGenerationException(maxCodeGenerationAttempts);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
