package com.naeil.study.document.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.entity.DocumentPolicy;
import com.naeil.study.document.exception.EmptyFileException;
import com.naeil.study.document.exception.FileCountExceededException;
import com.naeil.study.document.exception.FileSizeExceededException;
import com.naeil.study.document.exception.SessionStorageExceededException;
import com.naeil.study.document.exception.UnsupportedFileTypeException;
import com.naeil.study.document.validation.DocumentFileValidator.ValidatedFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("DocumentFileValidator - 업로드 검증")
class DocumentFileValidatorTest {

    private final DocumentFileValidator validator = new DocumentFileValidator();

    private MultipartFile file(String fileName, String contentType, int size) {
        byte[] content = new byte[size];
        return new MockMultipartFile("files", fileName, contentType, content);
    }

    private MultipartFile pdf(String fileName) {
        return new MockMultipartFile("files", fileName, "application/pdf",
                "pdf content".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("PDF, DOCX, TXT는 모두 통과하고 형식이 판별된다")
    void acceptsSupportedTypes() {
        List<MultipartFile> files = List.of(
                file("운영체제_1주차.pdf", "application/pdf", 100),
                file("정리.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 100),
                file("메모.txt", "text/plain", 100));

        List<ValidatedFile> validated = validator.validate(files, 0, 0);

        assertThat(validated).hasSize(3);
        assertThat(validated).extracting(ValidatedFile::fileType)
                .containsExactly(DocumentFileType.PDF, DocumentFileType.DOCX, DocumentFileType.TXT);
        assertThat(validated).extracting(ValidatedFile::originalFileName)
                .containsExactly("운영체제_1주차.pdf", "정리.docx", "메모.txt");
    }

    @Test
    @DisplayName("대문자 확장자도 허용한다")
    void acceptsUppercaseExtension() {
        List<ValidatedFile> validated = validator.validate(List.of(file("정리.PDF", "application/pdf", 10)), 0, 0);

        assertThat(validated.get(0).fileType()).isEqualTo(DocumentFileType.PDF);
    }

    @Test
    @DisplayName("경로가 섞인 파일명은 정규화해서 저장한다")
    void normalizesFileNameWithPath() {
        List<ValidatedFile> validated =
                validator.validate(List.of(file("../../../etc/passwd.pdf", "application/pdf", 10)), 0, 0);

        assertThat(validated.get(0).originalFileName()).isEqualTo("passwd.pdf");
    }

    @Test
    @DisplayName("MIME Type이 비어 있으면 확장자만으로 판단한다")
    void allowsMissingContentType() {
        List<ValidatedFile> validated = validator.validate(List.of(file("정리.pdf", null, 10)), 0, 0);

        assertThat(validated.get(0).fileType()).isEqualTo(DocumentFileType.PDF);
    }

    @Test
    @DisplayName("MIME Type이 octet-stream이면 확장자만으로 판단한다")
    void allowsOctetStream() {
        List<ValidatedFile> validated =
                validator.validate(List.of(file("정리.docx", "application/octet-stream", 10)), 0, 0);

        assertThat(validated.get(0).fileType()).isEqualTo(DocumentFileType.DOCX);
    }

    @Test
    @DisplayName("확장자와 MIME Type이 서로 어긋나면 거부한다")
    void rejectsMismatchedContentType() {
        assertThatThrownBy(() -> validator.validate(List.of(file("가짜.pdf", "image/png", 10)), 0, 0))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @ParameterizedTest(name = "{0} 거부")
    @ValueSource(strings = {"강의.ppt", "강의.pptx", "보고서.hwp", "사진.jpg", "그림.png", "묶음.zip", "악성.exe", "확장자없음"})
    @DisplayName("허용하지 않는 형식은 거부한다")
    void rejectsUnsupportedTypes(String fileName) {
        assertThatThrownBy(() -> validator.validate(List.of(file(fileName, null, 10)), 0, 0))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    @DisplayName("빈 파일은 거부한다")
    void rejectsEmptyFile() {
        MultipartFile empty = new MockMultipartFile("files", "빈파일.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> validator.validate(List.of(empty), 0, 0))
                .isInstanceOf(EmptyFileException.class);
    }

    @Test
    @DisplayName("파일이 하나도 없으면 거부한다")
    void rejectsNoFiles() {
        assertThatThrownBy(() -> validator.validate(List.of(), 0, 0))
                .isInstanceOf(EmptyFileException.class);
    }

    @Test
    @DisplayName("정상 파일 사이에 빈 파일이 섞여 있으면 전체를 거부한다")
    void rejectsWholeRequestWhenOneFileIsEmpty() {
        List<MultipartFile> files = List.of(
                pdf("정상.pdf"),
                new MockMultipartFile("files", "빈파일.pdf", "application/pdf", new byte[0]));

        assertThatThrownBy(() -> validator.validate(files, 0, 0))
                .isInstanceOf(EmptyFileException.class);
    }

    @Test
    @DisplayName("20MB를 넘는 파일은 거부한다")
    void rejectsOversizedFile() {
        MultipartFile oversized = new MockMultipartFile("files", "큰파일.pdf", "application/pdf",
                new byte[(int) DocumentPolicy.MAX_FILE_SIZE_BYTES + 1]);

        assertThatThrownBy(() -> validator.validate(List.of(oversized), 0, 0))
                .isInstanceOf(FileSizeExceededException.class);
    }

    @Test
    @DisplayName("정확히 20MB인 파일은 허용한다")
    void acceptsExactlyMaxSize() {
        MultipartFile exact = new MockMultipartFile("files", "딱맞음.pdf", "application/pdf",
                new byte[(int) DocumentPolicy.MAX_FILE_SIZE_BYTES]);

        assertThat(validator.validate(List.of(exact), 0, 0)).hasSize(1);
    }

    @Test
    @DisplayName("한 요청에 10개를 넘게 올리면 거부한다")
    void rejectsTooManyFilesInOneRequest() {
        List<MultipartFile> files = IntStream.range(0, 11)
                .mapToObj(i -> pdf("자료" + i + ".pdf"))
                .toList();

        assertThatThrownBy(() -> validator.validate(files, 0, 0))
                .isInstanceOf(FileCountExceededException.class);
    }

    @Test
    @DisplayName("이미 저장된 개수와 합쳐서 10개를 넘으면 거부한다")
    void rejectsWhenTotalCountExceeds() {
        List<MultipartFile> files = IntStream.range(0, 5)
                .mapToObj(i -> pdf("자료" + i + ".pdf"))
                .toList();

        assertThatThrownBy(() -> validator.validate(files, 7, 0))
                .isInstanceOf(FileCountExceededException.class);
    }

    @Test
    @DisplayName("합계가 정확히 10개면 허용한다")
    void acceptsWhenTotalCountIsExactlyMax() {
        List<MultipartFile> files = IntStream.range(0, 3)
                .mapToObj(i -> pdf("자료" + i + ".pdf"))
                .toList();

        assertThat(validator.validate(files, 7, 0)).hasSize(3);
    }

    @Test
    @DisplayName("세션 총 용량 100MB를 넘으면 거부한다")
    void rejectsWhenSessionStorageExceeds() {
        MultipartFile file = file("자료.pdf", "application/pdf", 1024);

        assertThatThrownBy(() -> validator.validate(List.of(file), 1, DocumentPolicy.MAX_TOTAL_SIZE_BYTES))
                .isInstanceOf(SessionStorageExceededException.class);
    }

    @Test
    @DisplayName("합계가 정확히 100MB면 허용한다")
    void acceptsWhenTotalSizeIsExactlyMax() {
        int size = 1024;
        MultipartFile file = file("자료.pdf", "application/pdf", size);

        assertThat(validator.validate(List.of(file), 1, DocumentPolicy.MAX_TOTAL_SIZE_BYTES - size)).hasSize(1);
    }
}
