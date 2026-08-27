package com.naeil.study.document.parser;

import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.exception.DocumentParseFailedException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TXT 텍스트 추출기.
 *
 * <p>공식 지원 인코딩은 UTF-8이다. 다만 한국어 TXT는 메모장에서 CP949로 저장되는 경우가
 * 흔해서, UTF-8 엄격 디코딩에 실패하면 MS949로 한 번 더 시도한다.
 * 두 번 다 실패하면 파싱 실패로 처리한다.
 *
 * <p>UTF-8 BOM은 제거한다. 남겨 두면 첫 글자가 보이지 않는 문자로 시작한다.
 */
@Component
public class TxtDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(TxtDocumentParser.class);

    /** UTF-8 BOM (U+FEFF). 눈에 보이지 않으므로 상수로 이름을 붙여 둔다. */
    private static final char BOM = '﻿';

    private static final String FALLBACK_CHARSET_NAME = "MS949";

    @Override
    public DocumentFileType supports() {
        return DocumentFileType.TXT;
    }

    @Override
    public ParsedDocument parse(InputStream inputStream) {
        byte[] bytes;
        try (InputStream in = inputStream) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            log.warn("txt reading failed: {}", e.getMessage());
            throw new DocumentParseFailedException("txt read failed");
        }

        String text = decodeStrictly(bytes, StandardCharsets.UTF_8);
        if (text == null) {
            text = decodeWithFallback(bytes);
        }
        return ParsedDocument.of(removeBom(text));
    }

    /**
     * 엄격 모드로 디코딩한다. 바이트가 해당 인코딩에 맞지 않으면 null을 돌려준다.
     *
     * <p>{@code new String(bytes, UTF_8)} 은 잘못된 바이트를 물음표로 바꿔 버려서
     * 인코딩이 틀렸다는 사실 자체를 알 수 없다. 그래서 디코더를 직접 쓴다.
     */
    private String decodeStrictly(byte[] bytes, Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private String decodeWithFallback(byte[] bytes) {
        try {
            String text = decodeStrictly(bytes, Charset.forName(FALLBACK_CHARSET_NAME));
            if (text == null) {
                throw new DocumentParseFailedException("txt decode failed");
            }
            log.info("txt decoded with fallback charset: {}", FALLBACK_CHARSET_NAME);
            return text;
        } catch (UnsupportedCharsetException e) {
            // MS949를 지원하지 않는 JVM. UTF-8 디코딩도 실패한 상태이므로 여기서 끝낸다.
            throw new DocumentParseFailedException("txt decode failed");
        }
    }

    private String removeBom(String text) {
        return !text.isEmpty() && text.charAt(0) == BOM ? text.substring(1) : text;
    }
}
