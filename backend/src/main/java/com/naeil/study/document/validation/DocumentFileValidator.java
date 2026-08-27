package com.naeil.study.document.validation;

import com.naeil.study.document.entity.DocumentFileType;
import com.naeil.study.document.entity.DocumentPolicy;
import com.naeil.study.document.exception.EmptyFileException;
import com.naeil.study.document.exception.FileCountExceededException;
import com.naeil.study.document.exception.FileSizeExceededException;
import com.naeil.study.document.exception.SessionStorageExceededException;
import com.naeil.study.document.exception.UnsupportedFileTypeException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 요청 전체를 저장 전에 검증한다.
 *
 * <p>한 요청은 전체 성공하거나 전체 실패한다. 그래서 파일을 하나씩 저장하면서 검사하지 않고,
 * <b>모든 파일을 먼저 검증한 뒤</b> 저장을 시작한다. 세 번째 파일이 잘못됐다고 해서
 * 앞의 두 개가 저장되어 있으면 안 된다.
 *
 * <p>검증을 통과하면 각 파일의 형식과 정규화된 파일명을 담은 {@link ValidatedFile} 목록을 돌려준다.
 * 호출자는 사용자 입력 파일명을 다시 해석할 필요가 없다.
 */
@Component
public class DocumentFileValidator {

    /**
     * 검증을 마친 파일. 파일명은 정규화되었고 형식은 허용 목록 안에 있다.
     */
    public record ValidatedFile(MultipartFile file, String originalFileName, DocumentFileType fileType) {

        public long size() {
            return file.getSize();
        }
    }

    /**
     * 업로드 요청을 검증한다.
     *
     * @param files            이번 요청의 파일들
     * @param existingCount    세션에 이미 저장된 파일 개수
     * @param existingTotalSize 세션에 이미 저장된 파일 총 용량(byte)
     * @throws FileCountExceededException      개수 한도 초과
     * @throws EmptyFileException              빈 파일 포함
     * @throws FileSizeExceededException       개별 파일 크기 초과
     * @throws UnsupportedFileTypeException    허용하지 않는 형식
     * @throws SessionStorageExceededException 세션 총 용량 초과
     */
    public List<ValidatedFile> validate(List<MultipartFile> files, long existingCount, long existingTotalSize) {
        if (files == null || files.isEmpty()) {
            throw new EmptyFileException();
        }
        if (files.size() > DocumentPolicy.MAX_FILE_COUNT
                || existingCount + files.size() > DocumentPolicy.MAX_FILE_COUNT) {
            throw new FileCountExceededException();
        }

        List<ValidatedFile> validated = new ArrayList<>(files.size());
        long uploadedSize = 0;
        for (MultipartFile file : files) {
            validated.add(validateSingle(file));
            uploadedSize += file.getSize();
        }

        if (existingTotalSize + uploadedSize > DocumentPolicy.MAX_TOTAL_SIZE_BYTES) {
            throw new SessionStorageExceededException();
        }
        return validated;
    }

    private ValidatedFile validateSingle(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new EmptyFileException();
        }
        if (file.getSize() > DocumentPolicy.MAX_FILE_SIZE_BYTES) {
            throw new FileSizeExceededException();
        }

        String originalFileName = DocumentPolicy.normalizeFileName(file.getOriginalFilename());
        if (originalFileName.isEmpty()) {
            throw new UnsupportedFileTypeException();
        }

        String extension = DocumentPolicy.extractExtension(originalFileName);
        DocumentFileType fileType = DocumentFileType.fromExtension(extension)
                .orElseThrow(UnsupportedFileTypeException::new);

        // MIME Type은 브라우저/OS에 따라 비어 있거나 다르게 오므로 값이 있을 때만 추가로 확인한다.
        if (!fileType.matchesContentType(file.getContentType())) {
            throw new UnsupportedFileTypeException();
        }
        return new ValidatedFile(file, originalFileName, fileType);
    }
}
