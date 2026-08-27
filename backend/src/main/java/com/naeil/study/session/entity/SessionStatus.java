package com.naeil.study.session.entity;

/**
 * 학습 세션의 생명주기 상태.
 *
 * <pre>
 * CREATED → UPLOADING → ANALYZING → READY → IN_PROGRESS → COMPLETED
 *                           ↓
 *                    ANALYSIS_FAILED  (다시 분석 요청 가능)
 *
 * 보관 기한이 지나면 어느 상태에서든 EXPIRED
 * </pre>
 *
 * <p>{@link #UPLOADING}은 "업로드가 진행 중"이 아니라 "강의자료를 등록하는 단계"를 뜻한다.
 * AI 분석을 시작하기 전까지 이 상태를 유지한다.
 */
public enum SessionStatus {

    CREATED,
    UPLOADING,
    ANALYZING,
    ANALYSIS_FAILED,
    READY,
    IN_PROGRESS,
    COMPLETED,
    EXPIRED
}
