package com.naeil.study.chat.client;

import com.naeil.study.chat.client.dto.AiChatAnswer;
import com.naeil.study.chat.client.dto.AiChatRequest;
import com.naeil.study.chat.context.StudyChatContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 를 부르지 않고 답을 만들어 내는 구현.
 *
 * <p>챗봇은 이 프로젝트에서 가장 자주 눌리는 기능이다. 화면을 한 번 확인할 때마다
 * 질문 몇 개를 던지게 되고, 그때마다 과금된다. {@code chat.ai-mode=mock} 이면 이 구현이 붙는다.
 *
 * <p><b>목 데이터임을 답변 안에 적는다.</b> 화면에서 실제 답변과 구분되지 않으면
 * "왜 이렇게 엉뚱하게 답하지?"를 한참 들여다보게 된다.
 *
 * <p>근거에 따라 답을 다르게 만든다. 자료 기반인지, 자료에서 관련 구간을 찾았는지에 따라
 * 화면 표시가 갈리므로, 목 모드에서도 그 세 갈래를 모두 볼 수 있어야 한다.
 */
public class MockAiChatClient implements AiChatClient {

    private static final Logger log = LoggerFactory.getLogger(MockAiChatClient.class);

    @Override
    public AiChatAnswer answer(AiChatRequest request) {
        StudyChatContext context = request.context();
        boolean fromMaterial = context.grounded() && !context.materialExcerpt().isBlank();

        String answer = """
                [목 응답입니다. 실제 AI 답변이 아닙니다.]
                질문: %s
                근거: %s
                지난 대화 %d줄을 함께 보고 답하는 자리입니다.
                실제 답변이 필요하면 LLM_MODE=gemini 로 실행하세요."""
                .formatted(shorten(request.question()), basisOf(context, fromMaterial), request.history().size());

        log.info("mock chat answered: grounded={}, fromMaterial={}, historySize={} (AI 를 호출하지 않았다)",
                context.grounded(), fromMaterial, request.history().size());
        return new AiChatAnswer(answer, fromMaterial);
    }

    private String basisOf(StudyChatContext context, boolean fromMaterial) {
        if (!context.grounded()) {
            return "올려주신 자료가 없어 일반적인 교과 지식 기준 (과목: %s, 범위: %s)"
                    .formatted(blankToDash(context.subject()), blankToDash(context.examScope()));
        }
        return fromMaterial
                ? "올려주신 강의자료 %d자 구간".formatted(context.materialExcerpt().length())
                : "강의자료는 있지만 이 질문과 관련된 구간을 찾지 못함";
    }

    /** 질문이 길어도 답변이 질문 전문으로 채워지지 않게 한다. */
    private String shorten(String question) {
        return question.length() <= 60 ? question : question.substring(0, 60) + "…";
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
