package com.naeil.study.document.entity;

import java.util.Locale;

/**
 * 강의자료 업로드 제한과 파일명 처리 규칙.
 *
 * <p>제한 값과 파일명 정규화 규칙을 한 곳에 모아 둔다.
 * 검증기, 테스트, 문서가 모두 이 상수를 참조한다.
 */
public final class DocumentPolicy {

    /** 한 세션에 보관할 수 있는 파일 개수. 한 요청의 파일 개수에도 같은 값을 적용한다. */
    public static final int MAX_FILE_COUNT = 10;

    /** 개별 파일 최대 크기 (20MB). */
    public static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    /** 한 세션의 전체 파일 용량 (100MB). */
    public static final long MAX_TOTAL_SIZE_BYTES = 100L * 1024 * 1024;

    /**
     * 파싱 성공으로 인정할 최소 텍스트 길이.
     *
     * <p>스캔본 PDF에서 의미 없는 특수문자 몇 개만 추출되는 경우가 있다.
     * 너무 높게 잡으면 짧은 정상 문서가 거부되므로 낮게 둔다.
     */
    public static final int MIN_EXTRACTED_TEXT_LENGTH = 20;

    /** DB 컬럼 길이에 맞춘 원본 파일명 최대 길이. */
    public static final int MAX_ORIGINAL_FILE_NAME_LENGTH = 255;

    private DocumentPolicy() {
    }

    /**
     * 업로드된 파일명에서 표시용 이름만 남긴다.
     *
     * <p>{@code ../../../etc/passwd} 처럼 경로가 섞여 들어오는 경우가 있다.
     * 경로 구분자 뒤의 마지막 조각만 취하고 제어문자를 제거한다.
     * 이 값은 화면 표시용일 뿐 실제 저장 경로에는 쓰지 않는다.
     *
     * @return 정규화된 파일명. 남는 글자가 없으면 빈 문자열
     */
    public static String normalizeFileName(String originalFileName) {
        if (originalFileName == null) {
            return "";
        }
        String name = originalFileName.replace('\\', '/');
        int lastSeparator = name.lastIndexOf('/');
        if (lastSeparator >= 0) {
            name = name.substring(lastSeparator + 1);
        }
        name = removeControlCharacters(name).strip();
        if (!name.isEmpty() && name.chars().allMatch(c -> c == '.')) {
            // "." 이나 ".." 만 남은 것은 파일명으로 볼 수 없다.
            return "";
        }
        if (name.length() > MAX_ORIGINAL_FILE_NAME_LENGTH) {
            name = name.substring(0, MAX_ORIGINAL_FILE_NAME_LENGTH);
        }
        return name;
    }

    /**
     * 파일명에서 확장자를 뽑는다.
     *
     * @return 점을 뺀 소문자 확장자. 확장자가 없으면 빈 문자열
     */
    public static String extractExtension(String fileName) {
        String normalized = normalizeFileName(fileName);
        int lastDot = normalized.lastIndexOf('.');
        if (lastDot < 0 || lastDot == normalized.length() - 1) {
            return "";
        }
        return normalized.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private static String removeControlCharacters(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isISOControl(c)) {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
