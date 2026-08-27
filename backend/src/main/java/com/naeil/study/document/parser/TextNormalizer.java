package com.naeil.study.document.parser;

import org.springframework.stereotype.Component;

/**
 * 추출한 텍스트를 최소한으로 다듬는다.
 *
 * <p><b>구조는 남기고 잡음만 없앤다.</b> 이후 AI가 제목, 소제목, 목록, 문단, 표를 구분해야 하므로
 * 줄바꿈을 지우지 않는다. 다음처럼 한 줄로 뭉개면 문서의 구조 정보가 사라진다.
 *
 * <pre>
 * 나쁨:  1장 프로세스 1.1 프로세스란 ... 1.2 스레드란 ...
 * 좋음:  1장 프로세스
 *
 *        1.1 프로세스란
 *        ...
 * </pre>
 *
 * <p><b>줄 안의 연속 공백은 건드리지 않는다.</b> {@code CPU    Scheduling} 을
 * {@code CPU Scheduling} 으로 줄일 수도 있지만, 코드·수식·표 정렬이 함께 무너진다.
 * 얻는 것보다 잃는 것이 크다고 보고 하지 않는다.
 */
@Component
public class TextNormalizer {

    /** 세 줄 이상 연속된 빈 줄을 하나로 줄이기 위한 기준. */
    private static final String THREE_OR_MORE_NEWLINES = "\n{3,}";

    private static final String PARAGRAPH_BREAK = "\n\n";

    // 보이지 않는 문자들. 소스에 그대로 쓰면 눈에 띄지 않으므로 코드 포인트로 둔다.
    private static final char NBSP = 0x00A0;
    private static final char FIGURE_SPACE = 0x2007;
    private static final char NARROW_NBSP = 0x202F;
    private static final char ZERO_WIDTH_SPACE = 0x200B;
    private static final char ZERO_WIDTH_NON_JOINER = 0x200C;
    private static final char ZERO_WIDTH_JOINER = 0x200D;
    private static final char BOM = 0xFEFF;

    public String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String normalized = unifyLineSeparators(text);
        normalized = replaceInvisibleCharacters(normalized);
        normalized = stripTrailingSpacesPerLine(normalized);
        normalized = normalized.replaceAll(THREE_OR_MORE_NEWLINES, PARAGRAPH_BREAK);
        return normalized.strip();
    }

    /** CRLF와 CR을 LF로 통일한다. */
    private String unifyLineSeparators(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * 보이지 않는 문자를 정리한다.
     *
     * <p>PDF에는 줄바꿈 없는 공백(U+00A0)과 폭 없는 공백이 흔히 섞여 들어온다.
     * 화면에는 공백처럼 보이지만 문자열 비교와 토큰 분리에서 다르게 동작한다.
     */
    private String replaceInvisibleCharacters(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isNonBreakingSpace(c)) {
                builder.append(' ');
            } else if (!isZeroWidth(c)) {
                builder.append(c);
            }
            // 폭 없는 문자는 버린다.
        }
        return builder.toString();
    }

    private boolean isNonBreakingSpace(char c) {
        return c == NBSP || c == FIGURE_SPACE || c == NARROW_NBSP;
    }

    private boolean isZeroWidth(char c) {
        return c == BOM || c == ZERO_WIDTH_SPACE || c == ZERO_WIDTH_NON_JOINER || c == ZERO_WIDTH_JOINER;
    }

    /**
     * 줄 끝 공백을 지운다. 줄 앞 들여쓰기는 남긴다.
     *
     * <p>들여쓰기는 목록과 계층 구조를 나타내는 정보이기 때문이다.
     */
    private String stripTrailingSpacesPerLine(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < lines.length; i++) {
            builder.append(stripTrailing(lines[i]));
            if (i < lines.length - 1) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
            end--;
        }
        return line.substring(0, end);
    }
}
