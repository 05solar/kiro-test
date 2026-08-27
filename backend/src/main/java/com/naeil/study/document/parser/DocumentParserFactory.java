package com.naeil.study.document.parser;

import com.naeil.study.document.entity.DocumentFileType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 파일 형식에 맞는 파서를 골라 준다.
 *
 * <p>스프링이 주입한 파서 목록을 형식별로 색인해 둔다.
 * 새 형식을 추가할 때 이 클래스는 고치지 않는다. {@link DocumentParser} 구현체만 등록하면 된다.
 */
@Component
public class DocumentParserFactory {

    private final Map<DocumentFileType, DocumentParser> parsers = new EnumMap<>(DocumentFileType.class);

    public DocumentParserFactory(List<DocumentParser> documentParsers) {
        for (DocumentParser parser : documentParsers) {
            DocumentParser previous = parsers.put(parser.supports(), parser);
            if (previous != null) {
                throw new IllegalStateException("duplicated parser for type: " + parser.supports());
            }
        }
        for (DocumentFileType type : DocumentFileType.values()) {
            if (!parsers.containsKey(type)) {
                // 업로드는 허용하는데 파서가 없으면 런타임에야 드러난다. 기동 시점에 막는다.
                throw new IllegalStateException("no parser registered for type: " + type);
            }
        }
    }

    public DocumentParser getParser(DocumentFileType fileType) {
        return parsers.get(fileType);
    }
}
