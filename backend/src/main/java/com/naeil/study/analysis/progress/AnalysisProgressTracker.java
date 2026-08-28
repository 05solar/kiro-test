package com.naeil.study.analysis.progress;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 진행 중인 분석의 실제 진행도.
 *
 * <p>분석은 동기 한 요청으로 처리되지만 안에서는 조각 수만큼 AI 를 부른다.
 * 화면이 가짜 타이머 대신 <b>실제로 몇 번째 조각을 읽고 있는지</b>를 보여줄 수 있게,
 * 서비스가 단계마다 여기에 기록하고 별도 조회 API 가 읽어 간다.
 *
 * <p><b>메모리에만 둔다.</b> 진행도는 다시 만들 수 있는 일시 상태라 DB 에 저장할 이유가
 * 없고, 실패해도 잃을 것이 없다. 서버가 재시작되면 진행 중이던 분석 자체가 끊기므로
 * 함께 사라지는 것이 맞다. 단일 인스턴스 전제이며, 수평 확장 시 공유 저장소로 옮긴다.
 */
@Component
public class AnalysisProgressTracker {

    public enum Phase {
        /** 진행 중인 분석이 없다 (기록 없음). */
        NONE,
        /** 자료를 조각으로 나누는 중. */
        PREPARING,
        /** 조각별 1차 분석 중. {@code completedChunks / totalChunks} 가 실제 진행도다. */
        ANALYZING,
        /** 후보를 최종 Topic 으로 합치는 중. */
        MERGING,
        /** 결과 저장 중. */
        SAVING,
        DONE,
        FAILED
    }

    /**
     * @param percent 화면 진행바용 0~100. 조각 분석 구간(5~85)에 실제 조각 비율을 편다
     */
    public record AnalysisProgress(Phase phase, int completedChunks, int totalChunks, int percent) {

        static AnalysisProgress of(Phase phase, int completed, int total) {
            return new AnalysisProgress(phase, completed, total, percentOf(phase, completed, total));
        }

        private static int percentOf(Phase phase, int completed, int total) {
            return switch (phase) {
                case NONE -> 0;
                case PREPARING -> 4;
                case ANALYZING -> total <= 0 ? 5 : 5 + Math.round(completed * 80f / total);
                case MERGING -> 90;
                case SAVING -> 96;
                case DONE -> 100;
                // 실패해도 0 으로 되돌리지 않는다. 어디까지 갔다가 실패했는지가 정보다
                case FAILED -> 100;
            };
        }
    }

    private static final AnalysisProgress NONE_PROGRESS = AnalysisProgress.of(Phase.NONE, 0, 0);

    private final Map<UUID, AnalysisProgress> bySession = new ConcurrentHashMap<>();

    public void preparing(UUID sessionId) {
        bySession.put(sessionId, AnalysisProgress.of(Phase.PREPARING, 0, 0));
    }

    public void analyzing(UUID sessionId, int completedChunks, int totalChunks) {
        bySession.put(sessionId, AnalysisProgress.of(Phase.ANALYZING, completedChunks, totalChunks));
    }

    public void merging(UUID sessionId) {
        advance(sessionId, Phase.MERGING);
    }

    public void saving(UUID sessionId) {
        advance(sessionId, Phase.SAVING);
    }

    /** 조각 총수는 ANALYZING 때 기록된 값을 그대로 이어받는다. */
    private void advance(UUID sessionId, Phase phase) {
        AnalysisProgress last = bySession.getOrDefault(sessionId, NONE_PROGRESS);
        bySession.put(sessionId, AnalysisProgress.of(phase, last.totalChunks(), last.totalChunks()));
    }

    public void done(UUID sessionId) {
        AnalysisProgress last = bySession.getOrDefault(sessionId, NONE_PROGRESS);
        bySession.put(sessionId, AnalysisProgress.of(Phase.DONE, last.totalChunks(), last.totalChunks()));
    }

    public void failed(UUID sessionId) {
        AnalysisProgress last = bySession.getOrDefault(sessionId, NONE_PROGRESS);
        bySession.put(sessionId,
                AnalysisProgress.of(Phase.FAILED, last.completedChunks(), last.totalChunks()));
    }

    /** 기록이 없으면 {@code NONE}. 분석을 시작한 적 없거나 서버가 재시작된 경우다. */
    public AnalysisProgress get(UUID sessionId) {
        return bySession.getOrDefault(sessionId, NONE_PROGRESS);
    }
}
