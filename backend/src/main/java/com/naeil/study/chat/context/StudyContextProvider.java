package com.naeil.study.chat.context;

import com.naeil.study.session.entity.StudySession;

/**
 * 질문에 답하는 데 필요한 만큼의 학습 맥락을 고른다.
 *
 * <p>세션이 가진 것을 전부 보내지 않는다. 강의자료 전문을 매 질문마다 실어 보내면
 * 비용이 질문 수만큼 늘고, 질문과 무관한 내용이 답변을 끌고 간다.
 *
 * <p>인터페이스로 둔 이유는 <b>고르는 방법이 바뀔 것을 알기 때문이다.</b> 지금은 키워드로
 * 문단을 고르지만(임베딩·벡터 검색 없음), 나중에 벡터 검색을 넣으면 이 구현만 갈아 끼운다.
 * 대화 서비스는 무엇을 어떻게 골랐는지 모른다.
 */
public interface StudyContextProvider {

    /**
     * @param session  대상 세션. 근거가 무엇인지(grounded)도 여기서 나온다
     * @param question 사용자가 방금 던진 질문. 관련 구간을 고르는 기준이 된다
     */
    StudyChatContext provide(StudySession session, String question);
}
