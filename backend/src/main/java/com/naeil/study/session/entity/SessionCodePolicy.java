package com.naeil.study.session.entity;

/**
 * 8자리 세션 코드의 형식 규칙.
 *
 * <p>코드 생성기({@code SessionCodeGenerator})와 입력값 검증이 같은 규칙을 공유하도록
 * 문자 집합과 길이를 이 한 곳에서만 정의한다.
 *
 * <p>혼동하기 쉬운 {@code 0, O, 1, I, L}은 문자 집합에서 제외한다.
 */
public final class SessionCodePolicy {

    /** 세션 코드에 사용할 수 있는 문자 (32자). */
    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** 세션 코드 길이. */
    public static final int LENGTH = 8;

    /** 세션 코드 형식 정규식. 어노테이션 상수로도 쓸 수 있도록 문자열로 둔다. */
    public static final String PATTERN = "^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$";

    private SessionCodePolicy() {
    }

    /**
     * 세션 코드 형식이 올바른지 검사한다. DB 조회 전에 호출한다.
     *
     * @param sessionCode 검사할 코드 (null 허용)
     * @return 길이가 8이고 허용 문자만 사용했으면 true
     */
    public static boolean isValid(String sessionCode) {
        if (sessionCode == null || sessionCode.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < sessionCode.length(); i++) {
            if (ALPHABET.indexOf(sessionCode.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
