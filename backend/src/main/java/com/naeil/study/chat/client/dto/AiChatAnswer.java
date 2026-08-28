package com.naeil.study.chat.client.dto;

/**
 * 챗봇의 답.
 *
 * <p>구조화 출력으로 받는다. 자연어 응답을 그대로 쓰면 모델이 앞뒤에 붙이는 인사말이나
 * 코드펜스를 화면에서 다시 걷어내야 한다.
 *
 * <p>{@code answeredFromMaterial} 은 <b>강의자료 구간에 실제로 근거가 있었는지</b>를
 * 모델이 스스로 표시한 값이다. 자료가 있는 세션이라도 질문이 자료 밖이면 false 다.
 * 화면은 이 값으로 "자료에 없어서 일반 지식으로 답했다"를 안내한다.
 *
 * <p>감싼 타입({@code Boolean})으로 둔다. 모델이 값을 빠뜨렸을 때 false 로 조용히 채워지면
 * "자료에 없다"는 잘못된 안내가 나간다. null 이면 검증 단계에서 드러나게 한다.
 */
public record AiChatAnswer(String answer, Boolean answeredFromMaterial) {
}
