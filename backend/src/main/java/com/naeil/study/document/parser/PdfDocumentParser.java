package com.naeil.study.document.parser;

import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.exception.DocumentParseFailedException;
import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PDF 텍스트 추출기. Apache PDFBox를 쓴다.
 *
 * <p><b>텍스트 레이어가 있는 PDF만 지원한다.</b> 스캔본처럼 이미지만 있는 PDF는
 * 여기서 빈 문자열이 나오고, 상위에서 {@code NO_EXTRACTABLE_TEXT} 로 처리된다.
 * OCR은 MVP 범위가 아니다.
 *
 * <p>줄바꿈은 지우지 않는다. 제목/소제목/목록 구조가 이후 AI 분석의 판단 근거가 되기 때문이다.
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentParser.class);

    @Override
    public DocumentFileType supports() {
        return DocumentFileType.PDF;
    }

    @Override
    public ParsedDocument parse(InputStream inputStream) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(inputStream))) {
            if (document.isEncrypted()) {
                // 암호가 걸린 PDF는 본문을 읽을 수 없다.
                throw new DocumentParseFailedException("encrypted pdf");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            stripper.setParagraphEnd("\n");
            return ParsedDocument.of(stripper.getText(document));
        } catch (IOException e) {
            log.warn("pdf parsing failed: {}", e.getMessage());
            throw new DocumentParseFailedException("pdf text extraction failed");
        } catch (RuntimeException e) {
            // 손상된 PDF에서 PDFBox가 IOException 이 아닌 예외를 던지는 경우가 있다.
            if (e instanceof DocumentParseFailedException failed) {
                throw failed;
            }
            log.warn("pdf parsing failed with unexpected error: {}", e.toString());
            throw new DocumentParseFailedException("pdf text extraction failed");
        }
    }
}
