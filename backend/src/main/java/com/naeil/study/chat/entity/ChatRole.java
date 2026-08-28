package com.naeil.study.chat.entity;

/**
 * 대화 한 줄을 누가 말했는지.
 *
 * <p>시스템 지시문은 여기 없다. 프롬프트의 시스템 자리에 따로 넣으며, 저장하지도 않는다.
 * 저장해 두면 다음 요청에서 사용자 발화와 같은 자리에 섞여 들어가고,
 * 그 순간 사용자가 시스템 지시문을 흉내 낼 수 있는 통로가 생긴다.
 */
public enum ChatRole {
    USER,
    ASSISTANT
}
