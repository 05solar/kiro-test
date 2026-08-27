package com.naeil.study.document.entity;

/**
 * 강의자료의 텍스트 추출 상태.
 *
 * <pre>
 * UPLOADED → PARSING → PARSED
 *                    ↘ PARSE_FAILED
 * </pre>
 *
 * <p>3단계에서는 업로드만 하므로 {@link #UPLOADED}만 사용한다.
 * 나머지 값은 4단계(텍스트 추출)에서 사용하기 위해 미리 정의해 둔다.
 */
public enum DocumentStatus {

    UPLOADED,
    PARSING,
    PARSED,
    PARSE_FAILED
}
