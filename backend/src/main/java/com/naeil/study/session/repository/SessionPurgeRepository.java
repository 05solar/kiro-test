package com.naeil.study.session.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 세션에 딸린 모든 데이터를 지운다.
 *
 * <p>연관관계마다 삭제 메서드를 리포지터리 여덟 곳에 흩어 두는 대신 한 곳에 모았다.
 * <b>순서가 곧 정확성</b>이라, 순서를 한눈에 볼 수 있는 편이 낫다.
 *
 * <pre>
 * StudySession
 *   ├─ Document
 *   ├─ StudyContext
 *   ├─ Topic ──┬─ Quiz ── QuizResult
 *   │          └─ StudyStep
 *   ├─ Curriculum ── StudyStep
 *   ├─ QuizResult
 *   ├─ ChatMessage
 *   └─ WrongAnswerSummary
 * </pre>
 *
 * <p>자식을 먼저 지운다. 순서를 어기면 외래키 제약에 걸려 트랜잭션 전체가 실패한다.
 * {@code StudyStep} 은 {@code Curriculum} 과 {@code Topic} 을 모두 참조하므로 둘보다 먼저 지운다.
 *
 * <p>벌크 삭제는 영속성 컨텍스트를 거치지 않는다. 호출한 트랜잭션 안에서 같은 엔티티를
 * 다시 읽지 않도록 주의한다.
 */
@Repository
public class SessionPurgeRepository {

    /** 자식부터 부모 순. 이 배열의 순서가 삭제 순서다. */
    private static final List<String> DELETE_IN_ORDER = List.of(
            "delete from QuizResult e where e.studySession.id in :ids",
            "delete from Quiz e where e.topic.id in (select t.id from Topic t where t.studySession.id in :ids)",
            "delete from StudyStep e where e.curriculum.id in (select c.id from Curriculum c where c.studySession.id in :ids)",
            "delete from Curriculum e where e.studySession.id in :ids",
            "delete from WrongAnswerSummary e where e.studySession.id in :ids",
            "delete from Topic e where e.studySession.id in :ids",
            "delete from StudyContext e where e.studySession.id in :ids",
            "delete from ChatMessage e where e.session.id in :ids",
            "delete from Document e where e.studySession.id in :ids",
            "delete from StudySession e where e.id in :ids"
    );

    private final EntityManager entityManager;

    public SessionPurgeRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * 주어진 세션들과 그에 딸린 모든 데이터를 지운다.
     *
     * <p>저장소의 실제 파일은 지우지 않는다. 호출하는 쪽이 DB보다 <b>먼저</b> 지운다.
     * 순서가 반대면 DB 레코드가 사라진 뒤 파일 삭제에 실패했을 때
     * 그 파일이 무엇이었는지 알 방법이 없어 영원히 남는다.
     *
     * @return 지운 세션 수
     */
    public int purge(List<UUID> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return 0;
        }
        int deletedSessions = 0;
        for (String jpql : DELETE_IN_ORDER) {
            deletedSessions = entityManager.createQuery(jpql)
                    .setParameter("ids", sessionIds)
                    .executeUpdate();
        }
        return deletedSessions;
    }
}
