package com.naeil.study.chat.context;

import java.util.List;

/**
 * 챗봇이 답할 때 근거로 쓰는 것들.
 *
 * <p>여기 담기지 않은 것은 답변의 근거가 되지 않는다. 프롬프트에 넣을 재료를 한곳에 모아
 * 두면 "무엇을 보고 답했는가"가 코드에서 그대로 읽힌다.
 *
 * @param grounded          실제 강의자료에 근거하는지. false 면 일반 지식으로 답한다
 * @param subject           과목명
 * @param examScope         시험 범위. 없으면 빈 문자열
 * @param topicOutline      학습 주제 요약. 자료가 없을 때는 이것이 사실상 유일한 근거다
 * @param materialExcerpt   강의자료에서 뽑은 구간. grounded 가 아니면 빈 문자열
 * @param currentStepTitle  지금 진행 중인 학습 단계. 없으면 빈 문자열
 */
public record StudyChatContext(
        boolean grounded,
        String subject,
        String examScope,
        List<String> topicOutline,
        String materialExcerpt,
        String currentStepTitle
) {

    /** 답할 근거가 하나도 없는지. 이때는 AI 를 부르지 않는다. */
    public boolean isEmpty() {
        return topicOutline.isEmpty() && materialExcerpt.isBlank();
    }
}
