package com.naeil.study.document.parser;

/**
 * 파서가 돌려주는 추출 결과.
 *
 * @param text 추출한 텍스트. 정규화는 호출자({@code DocumentParsingService})가 수행한다
 */
public record ParsedDocument(String text) {

    public static ParsedDocument of(String text) {
        return new ParsedDocument(text == null ? "" : text);
    }

    public int characterCount() {
        return text.length();
    }
}
