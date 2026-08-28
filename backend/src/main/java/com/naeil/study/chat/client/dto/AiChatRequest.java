package com.naeil.study.chat.client.dto;

import com.naeil.study.chat.context.StudyChatContext;
import java.util.List;

/**
 * 챗봇 한 번의 호출에 필요한 것 전부.
 *
 * @param context  근거로 쓸 학습 맥락. 여기 없는 것은 답변의 근거가 되지 않는다
 * @param history  지난 대화. 오래된 것부터. 서버가 저장한 것만 들어간다
 * @param question 방금 던진 질문
 */
public record AiChatRequest(
        StudyChatContext context,
        List<AiChatTurn> history,
        String question
) {
}
