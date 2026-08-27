package com.naeil.study.document.repository;

import com.naeil.study.document.entity.Document;
import com.naeil.study.document.entity.DocumentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** 세션의 강의자료를 업로드 순서대로 조회한다. */
    List<Document> findAllByStudySessionIdOrderByCreatedAtAsc(UUID sessionId);

    /**
     * 문서 ID와 세션 ID를 함께 조건으로 조회한다.
     *
     * <p>다른 세션의 문서를 조작하지 못하게 하려면 반드시 두 조건을 함께 걸어야 한다.
     * 문서 ID만으로 조회한 뒤 소유자를 비교하는 방식은 쓰지 않는다.
     */
    Optional<Document> findByIdAndStudySessionId(UUID documentId, UUID sessionId);

    /** 상태로 걸러 업로드 순서대로 조회한다. 전체 파싱 대상(UPLOADED)을 고를 때 쓴다. */
    List<Document> findAllByStudySessionIdAndStatusOrderByCreatedAtAsc(UUID sessionId, DocumentStatus status);

    /** 세션에 이미 올라간 파일 개수. 개수 제한 검사에 쓴다. */
    long countByStudySessionId(UUID sessionId);

    /** 세션에 이미 올라간 파일 총 용량(byte). 파일이 없으면 0을 돌려준다. */
    @Query("select coalesce(sum(d.fileSize), 0) from Document d where d.studySession.id = :sessionId")
    long sumFileSizeByStudySessionId(@Param("sessionId") UUID sessionId);
}
