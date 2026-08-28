package com.naeil.study.quiz.client.dto;

import com.naeil.study.analysis.client.dto.AiStudyContext;
import java.util.List;

/**
 * AI 퀴즈 생성 요청.
 *
 * <p>강의자료 전체가 아니라 Topic 과 관련된 추출 구간({@code sourceContext})만 보낸다.
 * 문제의 사실적 근거는 이 구간이고, 학습 맥락({@code studyContext})은 출제 방향을 조절하는
 * 힌트일 뿐이다. 프롬프트에서도 두 역할을 분리해 전달한다.
 *
 * @param sourceContext Topic 출처 문서에서 추출한 관련 텍스트
 * @param questionCount 생성할 문제 수 (설정값)
 * @param previousQuestions 이전 회차에 낸 문제의 <b>문장만</b>. 첫 회차면 비어 있다.
 *                          보기·정답·해설은 넣지 않는다 — 중복 판단에 필요 없고,
 *                          회차가 쌓일수록 프롬프트만 길어진다.
 */
public record AiQuizGenerationRequest(
        String subject,
        String topicTitle,
        String topicSummary,
        List<String> keyPoints,
        boolean professorEmphasisMatched,
        boolean pastExamMatched,
        boolean weakAreaMatched,
        boolean mustStudyMatched,
        AiStudyContext studyContext,
        String sourceContext,
        int questionCount,
        List<String> previousQuestions,
        /**
         * 실제 강의자료에 근거하는지.
         *
         * <p>false 면 근거가 표준 교과 지식이다. "자료에 있다"고 말하지 않도록
         * 프롬프트의 지시가 달라진다.
         */
        boolean grounded
) {

    /** 새 회차인지. 이전 문제가 있으면 프롬프트에 중복 방지 조건을 넣는다. */
    public boolean hasPrevious() {
        return previousQuestions != null && !previousQuestions.isEmpty();
    }
}
