package com.naeil.study.analysis.service;

import com.naeil.study.analysis.client.dto.AiStudyContext;
import com.naeil.study.session.entity.StudySourceType;
import java.util.List;
import java.util.UUID;

/**
 * 분석에 필요한 값을 트랜잭션 밖으로 꺼낸 것.
 *
 * <p>AI 호출은 수십 초 걸린다. 그동안 엔티티를 들고 있으면 DB 커넥션도 함께 잡힌다.
 * 필요한 값만 복사해 나온다.
 *
 * @param sourceType 무엇에 근거해 만들 것인지. 자료가 있으면 {@code USER_MATERIAL},
 *                   없으면 {@code GENERAL_KNOWLEDGE}
 * @param examScope  시험 범위. 자료가 없을 때는 이것이 유일한 근거다
 * @param documents  텍스트를 추출한 강의자료. 일반 지식 경로에서는 비어 있다
 */
public record AnalysisTarget(
        UUID sessionId,
        String subject,
        String examScope,
        StudySourceType sourceType,
        AiStudyContext studyContext,
        List<AnalysisDocument> documents
) {

    /** 실제 강의자료에 근거하는지. 프롬프트와 검증 방식이 갈린다. */
    public boolean isGrounded() {
        return sourceType.isGrounded();
    }

    public record AnalysisDocument(UUID documentId, String reference, String fileName, String text) {
    }
}
