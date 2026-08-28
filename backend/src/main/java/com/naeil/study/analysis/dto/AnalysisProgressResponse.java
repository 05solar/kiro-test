package com.naeil.study.analysis.dto;

import com.naeil.study.analysis.progress.AnalysisProgressTracker.AnalysisProgress;
import com.naeil.study.analysis.progress.AnalysisProgressTracker.Phase;

/**
 * 분석 진행도 응답.
 *
 * <p>화면 진행바가 가짜 타이머가 아니라 실제 조각 처리 수를 그리게 한다.
 *
 * @param percent 0~100. 조각 분석 구간에 실제 비율이 펴져 있다
 */
public record AnalysisProgressResponse(
        Phase phase,
        int completedChunks,
        int totalChunks,
        int percent
) {

    public static AnalysisProgressResponse from(AnalysisProgress progress) {
        return new AnalysisProgressResponse(
                progress.phase(),
                progress.completedChunks(),
                progress.totalChunks(),
                progress.percent());
    }
}
