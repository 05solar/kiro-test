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
        int questionCount
) {
}
