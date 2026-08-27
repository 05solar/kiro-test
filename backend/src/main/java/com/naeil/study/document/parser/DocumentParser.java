package com.naeil.study.document.parser;

import com.naeil.study.document.entity.DocumentFileType;
import java.io.InputStream;

/**
 * 파일 형식별 텍스트 추출기.
 *
 * <p>구현체는 스트림을 읽어 텍스트만 뽑는다. 정규화, 상태 변경, DB 저장은 하지 않는다.
 * 스트림은 구현체가 닫는다.
 *
 * <p>추출에 실패하면 {@link com.naeil.study.document.exception.DocumentParseFailedException}
 * 을 던진다. 라이브러리 예외를 그대로 올리지 않는다.
 */
public interface DocumentParser {

    /** 이 파서가 담당하는 파일 형식. */
    DocumentFileType supports();

    /** 스트림에서 텍스트를 추출한다. */
    ParsedDocument parse(InputStream inputStream);
}
