package com.naeil.study.document.parser;

import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.exception.DocumentParseFailedException;
import java.io.IOException;
import java.io.InputStream;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DOCX 텍스트 추출기. Apache POI를 쓴다.
 *
 * <p><b>본문 문단과 표 안의 텍스트를 모두 뽑는다.</b> 강의자료에서 표에 핵심 정리가
 * 들어 있는 경우가 많아, 표를 빠뜨리면 학습에 필요한 내용이 통째로 사라진다.
 *
 * <p>문서 본문 요소를 순서대로 훑기 때문에 문단과 표의 등장 순서가 유지된다.
 * XML의 완전한 시각적 순서(도형, 텍스트 박스 등)까지 복원하지는 않는다. MVP 범위 밖이다.
 *
 * <p>표의 셀은 탭으로, 행은 줄바꿈으로 구분한다. 표 구조를 어느 정도 남겨 두면
 * 이후 AI가 항목 나열임을 알아보기 쉽다.
 */
@Component
public class DocxDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocxDocumentParser.class);

    private static final String CELL_SEPARATOR = "\t";

    @Override
    public DocumentFileType supports() {
        return DocumentFileType.DOCX;
    }

    @Override
    public ParsedDocument parse(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder builder = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendParagraph(builder, paragraph);
                } else if (element instanceof XWPFTable table) {
                    appendTable(builder, table);
                }
            }
            return ParsedDocument.of(builder.toString());
        } catch (IOException | RuntimeException e) {
            log.warn("docx parsing failed: {}", e.toString());
            throw new DocumentParseFailedException("docx text extraction failed");
        }
    }

    private void appendParagraph(StringBuilder builder, XWPFParagraph paragraph) {
        String text = paragraph.getText();
        if (text != null && !text.isBlank()) {
            builder.append(text).append('\n');
        }
    }

    private void appendTable(StringBuilder builder, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            StringBuilder rowText = new StringBuilder();
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = cell.getText();
                if (cellText == null || cellText.isBlank()) {
                    continue;
                }
                if (!rowText.isEmpty()) {
                    rowText.append(CELL_SEPARATOR);
                }
                rowText.append(cellText.strip());
            }
            if (!rowText.isEmpty()) {
                builder.append(rowText).append('\n');
            }
        }
    }
}
