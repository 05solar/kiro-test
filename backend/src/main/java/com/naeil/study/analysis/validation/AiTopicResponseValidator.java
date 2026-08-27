package com.naeil.study.analysis.validation;

import com.naeil.study.analysis.client.dto.AiDocumentReference;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicResult;
import com.naeil.study.analysis.exception.AiAnalysisException;
import com.naeil.study.topic.entity.TopicImportance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 응답을 검증하고 저장 가능한 형태로 보정한다.
 *
 * <p><b>AI 응답을 그대로 DB에 넣지 않는다.</b> 구조화 출력을 쓰더라도 값의 범위와
 * 참조 무결성까지 보장되지는 않는다.
 *
 * <p>규칙을 두 갈래로 나눴다.
 *
 * <pre>
 * 실패로 처리 (분석 전체를 실패시킨다)
 *   - topics 가 비었다
 *   - title / summary 가 비었다
 *   - keyPoints 가 비었다
 *   - importance 가 정해진 값이 아니다
 *   - estimatedStudyMinutes 가 없다
 *
 * 보정으로 처리 (로그만 남기고 진행한다)
 *   - title 이 200자를 넘으면 자른다
 *   - estimatedStudyMinutes 를 5~120 범위로 맞춘다
 *   - keyPoints 중복을 제거한다
 *   - 모르는 문서 참조값을 버린다
 *   - Topic 수가 상한을 넘으면 앞에서부터 상한까지만 남긴다
 *   - matched 값이 없으면 false 로 본다
 * </pre>
 *
 * 구조가 깨진 응답은 다시 받아야 하지만, 값이 조금 벗어난 것까지 전체 실패로 만들면
 * 분석 한 번에 드는 비용과 시간을 매번 버리게 된다.
 */
@Component
public class AiTopicResponseValidator {

    private static final Logger log = LoggerFactory.getLogger(AiTopicResponseValidator.class);

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MIN_STUDY_MINUTES = 5;
    private static final int MAX_STUDY_MINUTES = 120;

    private final int maxTopics;

    public AiTopicResponseValidator(@Value("${ai.analysis.max-topics:30}") int maxTopics) {
        this.maxTopics = maxTopics;
    }

    /**
     * 최종 분석 결과를 검증한다.
     *
     * @param documents 요청에 실제로 넣은 문서 목록. 응답의 참조값은 이 안에 있어야 한다
     * @throws AiAnalysisException 구조가 규칙에 맞지 않는 경우
     */
    public List<ValidatedTopic> validate(AiTopicAnalysisResult result, List<AiDocumentReference> documents) {
        if (result == null || result.topics() == null || result.topics().isEmpty()) {
            throw new AiAnalysisException("ai returned no topics");
        }

        Map<String, UUID> referenceToId = new LinkedHashMap<>();
        for (AiDocumentReference document : documents) {
            referenceToId.put(document.reference(), document.documentId());
        }

        List<AiTopicResult> topics = result.topics();
        if (topics.size() > maxTopics) {
            log.warn("ai returned too many topics, truncating: returned={}, max={}", topics.size(), maxTopics);
            topics = topics.subList(0, maxTopics);
        }

        List<ValidatedTopic> validated = new ArrayList<>(topics.size());
        for (int i = 0; i < topics.size(); i++) {
            validated.add(validateTopic(topics.get(i), referenceToId, i + 1));
        }
        return validated;
    }

    private ValidatedTopic validateTopic(AiTopicResult topic, Map<String, UUID> referenceToId, int order) {
        String title = requireText(topic.title(), "title", order);
        if (title.length() > MAX_TITLE_LENGTH) {
            log.warn("topic title too long, truncating: order={}, length={}", order, title.length());
            title = title.substring(0, MAX_TITLE_LENGTH);
        }

        String summary = requireText(topic.summary(), "summary", order);
        List<String> keyPoints = validateKeyPoints(topic.keyPoints(), order);

        TopicImportance importance = TopicImportance.from(topic.importance())
                .orElseThrow(() -> new AiAnalysisException(
                        "invalid importance at topic " + order + ": " + topic.importance()));

        if (topic.estimatedStudyMinutes() == null) {
            throw new AiAnalysisException("missing estimatedStudyMinutes at topic " + order);
        }
        int minutes = clampStudyMinutes(topic.estimatedStudyMinutes(), order);

        return new ValidatedTopic(
                title,
                summary,
                keyPoints,
                importance,
                minutes,
                isTrue(topic.professorEmphasisMatched()),
                isTrue(topic.pastExamMatched()),
                isTrue(topic.weakAreaMatched()),
                isTrue(topic.mustStudyMatched()),
                resolveSourceDocuments(topic.sourceDocuments(), referenceToId, order),
                order);
    }

    private String requireText(String value, String field, int order) {
        if (value == null || value.isBlank()) {
            throw new AiAnalysisException("blank " + field + " at topic " + order);
        }
        return value.strip();
    }

    private List<String> validateKeyPoints(List<String> keyPoints, int order) {
        if (keyPoints == null || keyPoints.isEmpty()) {
            throw new AiAnalysisException("empty keyPoints at topic " + order);
        }
        // 순서를 유지하면서 중복을 없앤다. 여러 조각에서 같은 개념이 나올 수 있다.
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String keyPoint : keyPoints) {
            if (keyPoint != null && !keyPoint.isBlank()) {
                unique.add(keyPoint.strip());
            }
        }
        if (unique.isEmpty()) {
            throw new AiAnalysisException("empty keyPoints at topic " + order);
        }
        return List.copyOf(unique);
    }

    private int clampStudyMinutes(int minutes, int order) {
        if (minutes < MIN_STUDY_MINUTES || minutes > MAX_STUDY_MINUTES) {
            int clamped = Math.min(Math.max(minutes, MIN_STUDY_MINUTES), MAX_STUDY_MINUTES);
            log.warn("estimatedStudyMinutes out of range, clamping: order={}, value={}, clamped={}",
                    order, minutes, clamped);
            return clamped;
        }
        return minutes;
    }

    private boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    /**
     * 문서 참조값을 실제 UUID로 바꾼다.
     *
     * <p>모르는 참조값은 버린다. AI가 지어낸 값으로 존재하지 않는 문서를 가리키게 두지 않는다.
     * 전부 버려져 비어도 실패로 보지 않는다. 출처 추적은 부가 정보이고,
     * 그 때문에 분석 전체를 버리면 얻는 것보다 잃는 것이 크다.
     */
    private List<UUID> resolveSourceDocuments(
            List<String> references, Map<String, UUID> referenceToId, int order) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> resolved = new LinkedHashSet<>();
        for (String reference : references) {
            UUID documentId = referenceToId.get(reference == null ? null : reference.strip());
            if (documentId == null) {
                log.warn("unknown source document reference, dropping: order={}, reference={}", order, reference);
                continue;
            }
            resolved.add(documentId);
        }
        return List.copyOf(resolved);
    }
}
