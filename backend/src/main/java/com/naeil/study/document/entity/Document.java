package com.naeil.study.document.entity;

import com.naeil.study.session.entity.StudySession;
import com.naeil.study.storage.StoredFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 업로드된 강의자료 한 건의 메타데이터.
 *
 * <p>파일 본문은 DB에 넣지 않는다. 실제 파일은 Storage에 두고 여기에는 위치만 남긴다.
 *
 * <p>연관관계는 Document → StudySession 단방향이다. 세션이 자기 문서를 알아야 할 일이
 * 아직 없으므로 양방향으로 만들지 않는다.
 *
 * <p><b>파일 이름을 둘로 나눈 이유</b>
 * <pre>
 * originalFileName  사용자가 올린 이름. 화면 표시에만 쓴다
 * storedFileName    Storage 안의 실제 이름(UUID). 충돌과 경로 조작을 막는다
 * </pre>
 */
@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    /** DB 컬럼 길이에 맞춘 실패 원인 최대 길이. */
    private static final int MAX_PARSE_ERROR_MESSAGE_LENGTH = 500;

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private StudySession studySession;

    /** 사용자가 업로드한 원래 파일 이름. 표시용이며 경로 생성에 쓰지 않는다. */
    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    /** Storage 안의 실제 파일 이름. UUID 기반이라 충돌하지 않는다. */
    @Column(name = "stored_file_name", nullable = false, updatable = false, length = 100)
    private String storedFileName;

    /** Storage root 기준 상대 경로. 4단계 파서가 이 값으로 원본을 다시 읽는다. */
    @Column(name = "storage_path", nullable = false, updatable = false, length = 500)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private DocumentFileType fileType;

    /** 파일 크기(byte). 세션 전체 용량 제한 계산에 쓴다. */
    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    /**
     * 파일에서 추출한 텍스트. 원본에 가까운 상태로 보관한다.
     *
     * <p>{@link DocumentStatus#PARSED} 일 때만 값이 있다.
     * AI 분석은 이 값을 입력으로 쓰고, 분석 결과는 별도 테이블에 저장한다.
     *
     * <p><b>{@code @Lob} 을 붙이지 않는다.</b> PostgreSQL에서 {@code @Lob String} 은
     * JDBC 드라이버가 large object로 저장해, 컬럼에는 본문 대신 OID 숫자가 들어간다.
     * H2에서는 정상 동작해서 테스트로는 드러나지 않는다.
     * {@code columnDefinition = "TEXT"} 만으로 길이 제한 없는 컬럼이 만들어진다.
     */
    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    /** 추출한 텍스트 길이. 목록 화면에서 파싱 결과를 가늠하는 데 쓴다. */
    @Column(name = "character_count")
    private Integer characterCount;

    /** 파싱에 성공한 시각. 실패하거나 아직 파싱하지 않았으면 null이다. */
    @Column(name = "parsed_at")
    private LocalDateTime parsedAt;

    /**
     * 파싱 실패 원인 요약. 내부 진단용이며 사용자 응답에 그대로 내보내지 않는다.
     *
     * <p>스택트레이스나 라이브러리 원문 메시지를 통째로 저장하지 않는다.
     */
    @Column(name = "parse_error_message", length = 500)
    private String parseErrorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Document(
            StudySession studySession,
            String originalFileName,
            StoredFile storedFile,
            DocumentFileType fileType,
            long fileSize,
            LocalDateTime now
    ) {
        this.studySession = studySession;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFile.storedFileName();
        this.storagePath = storedFile.storagePath();
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.status = DocumentStatus.UPLOADED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 업로드가 끝난 강의자료를 만든다. 상태는 항상 {@link DocumentStatus#UPLOADED}로 시작한다.
     *
     * @param originalFileName 정규화를 마친 표시용 파일명
     * @param storedFile       Storage가 돌려준 저장 위치
     */
    public static Document create(
            StudySession studySession,
            String originalFileName,
            StoredFile storedFile,
            DocumentFileType fileType,
            long fileSize,
            LocalDateTime now
    ) {
        return new Document(studySession, originalFileName, storedFile, fileType, fileSize, now);
    }

    /** 이 문서가 해당 세션 소유인지 확인한다. */
    public boolean belongsTo(UUID sessionId) {
        return studySession.getId().equals(sessionId);
    }

    public boolean isParsing() {
        return status == DocumentStatus.PARSING;
    }

    public boolean isParsed() {
        return status == DocumentStatus.PARSED;
    }

    /** 아직 파싱하지 않았거나 실패해서 다시 시도할 수 있는 상태인지. */
    public boolean isParsable() {
        return status == DocumentStatus.UPLOADED || status == DocumentStatus.PARSE_FAILED;
    }

    /**
     * 파싱을 시작한다. {@code UPLOADED} 또는 {@code PARSE_FAILED} 에서만 넘어간다.
     *
     * <p>재시도일 수 있으므로 이전 실패 기록을 지운다.
     */
    public void startParsing(LocalDateTime now) {
        if (!isParsable()) {
            throw new IllegalStateException("cannot start parsing from status: " + status);
        }
        this.status = DocumentStatus.PARSING;
        this.parseErrorMessage = null;
        this.parsedAt = null;
        this.updatedAt = now;
    }

    /** 파싱에 성공했다. 추출한 텍스트와 길이, 완료 시각을 함께 기록한다. */
    public void markParsed(String extractedText, LocalDateTime now) {
        this.extractedText = extractedText;
        this.characterCount = extractedText.length();
        this.status = DocumentStatus.PARSED;
        this.parseErrorMessage = null;
        this.parsedAt = now;
        this.updatedAt = now;
    }

    /**
     * 파싱에 실패했다. 원인을 요약해 남기고 다시 시도할 수 있는 상태로 둔다.
     *
     * @param reason 내부 진단용 요약. 길면 잘라서 저장한다
     */
    public void markParseFailed(String reason, LocalDateTime now) {
        this.status = DocumentStatus.PARSE_FAILED;
        this.parseErrorMessage = truncateReason(reason);
        this.extractedText = null;
        this.characterCount = null;
        this.parsedAt = null;
        this.updatedAt = now;
    }

    private static String truncateReason(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() > MAX_PARSE_ERROR_MESSAGE_LENGTH
                ? reason.substring(0, MAX_PARSE_ERROR_MESSAGE_LENGTH)
                : reason;
    }
}
