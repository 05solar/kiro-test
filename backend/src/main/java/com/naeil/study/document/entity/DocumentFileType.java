package com.naeil.study.document.entity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 업로드를 허용하는 강의자료 형식.
 *
 * <p>확장자와 MIME Type을 함께 들고 있다. 브라우저나 OS에 따라 MIME Type이 비어 있거나
 * 다르게 오는 경우가 있으므로, 확장자를 먼저 판단하고 MIME Type은 값이 있을 때만 추가로 확인한다.
 *
 * <p>PPT/PPTX는 MVP 범위에서 제외한다.
 */
public enum DocumentFileType {

    PDF("pdf", Set.of("application/pdf")),
    DOCX("docx", Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/zip"
    )),
    TXT("txt", Set.of("text/plain"));

    /** MIME Type을 판단할 수 없을 때 흔히 들어오는 값들. 이 경우 확장자만으로 판단한다. */
    private static final Set<String> UNKNOWN_CONTENT_TYPES = Set.of(
            "application/octet-stream",
            "application/x-zip-compressed",
            "binary/octet-stream"
    );

    private final String extension;
    private final Set<String> contentTypes;

    DocumentFileType(String extension, Set<String> contentTypes) {
        this.extension = extension;
        this.contentTypes = contentTypes;
    }

    public String getExtension() {
        return extension;
    }

    /** 확장자로 형식을 찾는다. 대소문자를 구분하지 않는다. */
    public static Optional<DocumentFileType> fromExtension(String extension) {
        if (extension == null) {
            return Optional.empty();
        }
        String normalized = extension.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.extension.equals(normalized))
                .findFirst();
    }

    /**
     * MIME Type이 이 형식과 어울리는지 확인한다.
     *
     * <p>값이 없거나 일반적인 바이너리 타입이면 판단을 보류하고 통과시킨다.
     * 정상 파일이 브라우저 차이로 거부되는 편보다, 확장자 검증에 기대는 편이 낫다.
     */
    public boolean matchesContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).split(";")[0].strip();
        if (UNKNOWN_CONTENT_TYPES.contains(normalized)) {
            return true;
        }
        return contentTypes.contains(normalized);
    }
}
