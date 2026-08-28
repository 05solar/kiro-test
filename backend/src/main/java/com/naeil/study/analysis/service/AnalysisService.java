package com.naeil.study.analysis.service;

import com.naeil.study.analysis.chunk.DocumentChunk;
import com.naeil.study.analysis.chunk.DocumentChunker;
import com.naeil.study.analysis.client.AiAnalysisClient;
import com.naeil.study.analysis.client.dto.AiChunkAnalysisRequest;
import com.naeil.study.analysis.client.dto.AiDocumentReference;
import com.naeil.study.analysis.client.dto.AiGeneralKnowledgeRequest;
import com.naeil.study.analysis.client.dto.AiSourcedTopicCandidate;
import com.naeil.study.analysis.client.dto.AiTopicAnalysisResult;
import com.naeil.study.analysis.client.dto.AiTopicCandidate;
import com.naeil.study.analysis.client.dto.AiTopicCandidates;
import com.naeil.study.analysis.client.dto.AiTopicMergeRequest;
import com.naeil.study.analysis.exception.AiAnalysisException;
import com.naeil.study.analysis.service.AnalysisTarget.AnalysisDocument;
import com.naeil.study.analysis.validation.AiTopicResponseValidator;
import com.naeil.study.analysis.validation.ValidatedTopic;
import com.naeil.study.session.entity.StudySession;
import com.naeil.study.session.service.SessionService;
import com.naeil.study.topic.entity.Topic;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 강의자료를 AI로 분석해 Topic을 만든다.
 *
 * <pre>
 * 검증 → ANALYZING → 조각 나누기 → 조각별 1차 분석 → 최종 통합 → 응답 검증 → Topic 저장 → READY
 *                                                                              ↘ ANALYSIS_FAILED
 * </pre>
 *
 * <p><b>이 클래스에는 {@code @Transactional} 이 없다.</b> AI 호출이 오래 걸리므로
 * 그 시간 동안 DB 커넥션을 잡지 않는다. 상태 변경은 {@link AnalysisStateWriter}의
 * 짧은 트랜잭션으로 나눠 처리한다.
 *
 * <p><b>남은 학습 시간을 여기서 쓰지 않는다.</b> 시간이 부족하다고 Topic을 빼면
 * 자료 분석과 시간 배분이 뒤섞인다. 전체 Topic을 만들어 두고, 남은 시간에 맞춘 조정은
 * 다음 커리큘럼 단계에서 한다. 그래서 Topic 예상시간 합이 남은 시간을 넘어도 정상이다.
 *
 * <p>동기로 처리한다. 강의자료가 커지면 오래 걸리므로 이후 비동기로 옮길 수 있게
 * 호출 흐름을 컨트롤러 밖에 두었다.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final SessionService sessionService;
    private final AnalysisStateWriter stateWriter;
    private final DocumentChunker documentChunker;
    private final AiAnalysisClient aiAnalysisClient;
    private final AiTopicResponseValidator validator;
    private final int maxTopics;

    public AnalysisService(
            SessionService sessionService,
            AnalysisStateWriter stateWriter,
            DocumentChunker documentChunker,
            AiAnalysisClient aiAnalysisClient,
            AiTopicResponseValidator validator,
            @Value("${ai.analysis.max-topics:30}") int maxTopics
    ) {
        this.sessionService = sessionService;
        this.stateWriter = stateWriter;
        this.documentChunker = documentChunker;
        this.aiAnalysisClient = aiAnalysisClient;
        this.validator = validator;
        this.maxTopics = maxTopics;
    }

    /**
     * 세션의 강의자료를 분석해 Topic을 만든다.
     *
     * <p>이미 Topic이 있으면 통째로 교체한다. 자료나 학습 맥락이 바뀐 뒤 다시 분석하면
     * 이전 결과는 낡은 분석이기 때문이다.
     *
     * @throws com.naeil.study.analysis.exception.ExamInfoRequiredException       시험 정보 없음
     * @throws com.naeil.study.analysis.exception.NoParsedDocumentException       분석할 자료 없음
     * @throws com.naeil.study.analysis.exception.AnalysisAlreadyRunningException 이미 분석 중
     * @throws AiAnalysisException                                               분석 실패
     */
    public List<Topic> analyze(String sessionCode) {
        StudySession session = sessionService.getSessionAndTouch(sessionCode);
        AnalysisTarget target = stateWriter.beginAnalysis(session.getId());
        UUID sessionId = target.sessionId();

        try {
            List<Topic> topics = runAnalysis(target);
            log.info("analysis finished: sessionId={}, documents={}, topics={}",
                    sessionId, target.documents().size(), topics.size());
            return topics;
        } catch (RuntimeException e) {
            stateWriter.failAnalysis(sessionId);
            log.warn("analysis failed: sessionId={}, reason={}", sessionId, describe(e));
            throw e;
        }
    }

    /**
     * 근거에 따라 두 갈래로 나뉜다.
     *
     * <pre>
     * 자료 있음  조각 분석 → 통합 → 검증
     * 자료 없음  과목명 + 시험 범위 → 주제 생성 → 검증
     * </pre>
     *
     * <p>갈라지는 곳은 여기 하나다. 검증·저장·이후 계획 생성은 같은 길을 지난다.
     * 두 경로를 끝까지 따로 만들면 한쪽만 고치는 실수가 난다.
     */
    private List<Topic> runAnalysis(AnalysisTarget target) {
        List<ValidatedTopic> validated = target.isGrounded()
                ? fromDocuments(target)
                : fromGeneralKnowledge(target);

        return stateWriter.completeAnalysis(target.sessionId(), validated);
    }

    /** 강의자료에서 뽑는다. 조각별로 후보를 모으고 한 번에 통합한다. */
    private List<ValidatedTopic> fromDocuments(AnalysisTarget target) {
        List<AiDocumentReference> references = toReferences(target.documents());
        List<AiSourcedTopicCandidate> candidates = collectCandidates(target);

        if (candidates.isEmpty()) {
            // 자료는 있는데 학습 주제를 하나도 찾지 못한 경우. 목차만 있는 파일 등이 해당한다.
            throw new AiAnalysisException("no topic candidates extracted from documents");
        }

        AiTopicMergeRequest mergeRequest = new AiTopicMergeRequest(
                target.subject(), target.studyContext(), references, candidates, maxTopics);
        return mergeAndValidate(mergeRequest, references);
    }

    /**
     * 과목명과 시험 범위만으로 만든다.
     *
     * <p>출처 문서 참조가 없다. 검증기에 빈 목록을 넘기면 AI 가 지어낸 참조값이 전부 버려진다.
     * 자료가 없는데 출처가 붙어 있으면 그건 지어낸 것이다.
     *
     * <p>조각 분석이 없어 AI 호출이 <b>한 번</b>이다. 자료 기반은 조각 수만큼 부른다.
     */
    private List<ValidatedTopic> fromGeneralKnowledge(AnalysisTarget target) {
        AiGeneralKnowledgeRequest request = new AiGeneralKnowledgeRequest(
                target.subject(), target.examScope(), target.studyContext(), maxTopics);

        log.info("general knowledge analysis: sessionId={}, subject={}, scope chars={}",
                target.sessionId(), target.subject(),
                target.examScope() == null ? 0 : target.examScope().length());

        try {
            return validator.validate(
                    aiAnalysisClient.generateFromGeneralKnowledge(request), List.of());
        } catch (AiAnalysisException first) {
            // 통합 경로와 같은 정책이다. 형식 오류는 한 번 더 물어본다.
            log.warn("general knowledge result rejected, retrying once: reason={}", first.getReason());
            return validator.validate(
                    aiAnalysisClient.generateFromGeneralKnowledge(request), List.of());
        }
    }

    /**
     * 문서를 조각으로 나눠 1차 분석을 돌린다.
     *
     * <p>조각 하나가 실패하면 분석 전체를 실패로 본다. 일부 자료가 빠진 채 만들어진
     * 커리큘럼은 사용자에게 더 나쁘다. 실패를 감추는 것보다 다시 시도하게 하는 편이 낫다.
     */
    private List<AiSourcedTopicCandidate> collectCandidates(AnalysisTarget target) {
        List<AiSourcedTopicCandidate> candidates = new ArrayList<>();
        int totalChunks = 0;

        for (AnalysisDocument document : target.documents()) {
            List<DocumentChunk> chunks =
                    documentChunker.chunk(document.documentId(), document.fileName(), document.text());
            totalChunks += chunks.size();

            for (DocumentChunk chunk : chunks) {
                AiChunkAnalysisRequest request = new AiChunkAnalysisRequest(
                        target.subject(),
                        document.reference(),
                        document.fileName(),
                        chunk.chunkIndex(),
                        chunks.size(),
                        chunk.text());

                AiTopicCandidates result = aiAnalysisClient.analyzeChunk(request);
                if (result == null || result.topics() == null) {
                    continue;
                }
                for (AiTopicCandidate candidate : result.topics()) {
                    if (isUsable(candidate)) {
                        candidates.add(new AiSourcedTopicCandidate(
                                document.reference(), document.fileName(), chunk.chunkIndex(), candidate));
                    }
                }
            }
        }

        log.info("chunk analysis finished: sessionId={}, chunks={}, candidates={}",
                target.sessionId(), totalChunks, candidates.size());
        return candidates;
    }

    /**
     * 최종 통합을 실행하고 응답을 검증한다.
     *
     * <p>검증에 실패하면 한 번만 다시 요청한다. 형식이 어긋나는 응답은 다시 받으면
     * 고쳐지는 경우가 있다. 두 번째도 실패하면 그대로 실패로 둔다.
     * 무한 재시도는 비용만 늘린다.
     */
    private List<ValidatedTopic> mergeAndValidate(
            AiTopicMergeRequest request, List<AiDocumentReference> references) {
        try {
            AiTopicAnalysisResult result = aiAnalysisClient.mergeTopics(request);
            return validator.validate(result, references);
        } catch (AiAnalysisException first) {
            log.warn("merge result rejected, retrying once: reason={}", first.getReason());
            AiTopicAnalysisResult retried = aiAnalysisClient.mergeTopics(request);
            return validator.validate(retried, references);
        }
    }

    private List<AiDocumentReference> toReferences(List<AnalysisDocument> documents) {
        return documents.stream()
                .map(document -> new AiDocumentReference(
                        document.reference(), document.documentId(), document.fileName()))
                .toList();
    }

    /** 후보가 최소한의 내용을 갖췄는지. 빈 후보를 통합 단계까지 끌고 가지 않는다. */
    private boolean isUsable(AiTopicCandidate candidate) {
        return candidate != null
                && candidate.title() != null && !candidate.title().isBlank()
                && candidate.summary() != null && !candidate.summary().isBlank()
                && candidate.keyPoints() != null && !candidate.keyPoints().isEmpty();
    }

    /** 로그용 요약. 강의자료 내용이나 AI 응답 전문이 로그에 남지 않게 한다. */
    private String describe(RuntimeException e) {
        if (e instanceof AiAnalysisException analysisException) {
            return analysisException.getReason();
        }
        return e.getClass().getSimpleName();
    }
}
