package com.naeil.study.analysis.client.dto;

/**
 * AI에게 전달하는 사용자 제공 학습 맥락.
 *
 * <p>네 값 모두 {@code null}일 수 있다. 학습 맥락은 선택 입력이며, 없어도 분석은 정상 진행된다.
 *
 * <p><b>이 내용은 검증된 사실이 아니라 사용자가 준 참고 정보다.</b>
 * 프롬프트에서도 시스템 지시문과 분리해 데이터로만 전달한다.
 */
public record AiStudyContext(
        String professorEmphasis,
        String pastExamInfo,
        String weakAreas,
        String mustStudyAreas
) {

    public static AiStudyContext empty() {
        return new AiStudyContext(null, null, null, null);
    }

    public boolean isEmpty() {
        return professorEmphasis == null && pastExamInfo == null
                && weakAreas == null && mustStudyAreas == null;
    }
}
