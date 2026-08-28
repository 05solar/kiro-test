package com.naeil.study.session.service;

import com.naeil.study.document.entity.Document;
import com.naeil.study.document.repository.DocumentRepository;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.repository.SessionPurgeRepository;
import com.naeil.study.session.repository.StudySessionRepository;
import com.naeil.study.storage.StorageService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관 기한이 지난 세션과 그 파일을 지운다.
 *
 * <p>세션은 마지막 접근 후 30일까지만 보관한다고 사용자에게 안내한다.
 * 지우는 쪽이 없으면 그 약속이 지켜지지 않고, 디스크와 DB가 무한정 늘어난다.
 *
 * <p><b>파일을 먼저 지우고 DB를 지운다.</b> 순서가 반대면 DB 레코드가 사라진 뒤
 * 파일 삭제에 실패했을 때 그 파일이 무엇이었는지 알 방법이 없어 영원히 남는다.
 * 반대로 파일만 지워지고 DB가 남으면 다음 실행에서 다시 시도한다.
 *
 * <p>한 번에 지우는 개수를 제한한다. 오래 방치된 환경에서 첫 실행이 수만 건을 한 트랜잭션으로
 * 처리하면 DB가 오래 잠긴다. 남은 것은 다음 주기에 지운다.
 *
 * <p>인스턴스를 여러 대로 늘리면 모두가 이 작업을 돌린다. 같은 세션을 두 번 지우려 해도
 * 결과는 같지만, 규모가 커지면 락이나 전용 배치로 분리한다.
 */
@Component
public class ExpiredSessionCleaner {

    private static final Logger log = LoggerFactory.getLogger(ExpiredSessionCleaner.class);

    private final StudySessionRepository studySessionRepository;
    private final SessionPurgeRepository sessionPurgeRepository;
    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final Clock clock;
    private final boolean enabled;
    private final int batchSize;

    public ExpiredSessionCleaner(
            StudySessionRepository studySessionRepository,
            SessionPurgeRepository sessionPurgeRepository,
            DocumentRepository documentRepository,
            StorageService storageService,
            Clock clock,
            @Value("${session.cleanup.enabled:true}") boolean enabled,
            @Value("${session.cleanup.batch-size:200}") int batchSize
    ) {
        this.studySessionRepository = studySessionRepository;
        this.sessionPurgeRepository = sessionPurgeRepository;
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.clock = clock;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    /**
     * 만료된 세션을 정리한다. 기본은 매일 새벽 4시(애플리케이션 시간대 기준)다.
     *
     * <p>실패해도 예외를 밖으로 던지지 않는다. 정리 작업이 죽었다고 다음 주기까지 막힐 이유가 없다.
     */
    @Scheduled(cron = "${session.cleanup.cron:0 0 4 * * *}", zone = "${app.timezone:Asia/Seoul}")
    public void cleanUp() {
        if (!enabled) {
            return;
        }
        try {
            int deleted = deleteExpired();
            if (deleted > 0) {
                log.info("expired sessions cleaned up: {}", deleted);
            }
        } catch (RuntimeException e) {
            log.error("expired session cleanup failed", e);
        }
    }

    /**
     * 만료된 세션을 지우고 지운 개수를 돌려준다.
     *
     * <p>테스트와 수동 실행을 위해 공개한다.
     */
    @Transactional
    public int deleteExpired() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<StudySession> expired = studySessionRepository
                .findByExpiresAtBeforeOrderByExpiresAtAsc(now, PageRequest.of(0, batchSize));
        if (expired.isEmpty()) {
            return 0;
        }

        // 파일을 먼저 지운다. DB 를 먼저 지우면 삭제에 실패한 파일의 위치를 잃는다.
        expired.forEach(this::deleteFiles);
        return sessionPurgeRepository.purge(expired.stream().map(StudySession::getId).toList());
    }

    /**
     * 세션에 딸린 실제 파일을 지운다.
     *
     * <p>파일 하나가 실패해도 나머지를 계속 지운다. 한 파일 때문에 세션 전체가 남으면
     * 다음 실행에서 같은 자리에서 또 막힌다.
     */
    private void deleteFiles(StudySession session) {
        for (Document document : documentRepository.findAllByStudySessionIdOrderByCreatedAtAsc(session.getId())) {
            try {
                storageService.delete(document.getStoragePath());
            } catch (RuntimeException e) {
                log.warn("failed to delete stored file during cleanup: sessionId={}, path={}",
                        session.getId(), document.getStoragePath(), e);
            }
        }
    }
}
