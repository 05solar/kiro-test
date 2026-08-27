package com.naeil.study.storage;

import java.io.InputStream;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장소 추상화.
 *
 * <p>도메인 서비스는 이 인터페이스만 알고, 로컬 파일 시스템인지 S3인지는 모른다.
 * MVP는 {@link LocalStorageService}를 쓰고, 배포 시 S3 호환 구현으로 교체한다.
 *
 * <p>경로는 세션 단위로 나눈다. 외부에 노출되는 8자리 세션 코드가 아니라
 * 내부 UUID인 {@code StudySession.id}를 쓴다.
 *
 * <pre>
 * sessions/{sessionId}/documents/{storedFileName}
 * </pre>
 */
public interface StorageService {

    /**
     * 파일을 저장하고 저장 위치를 돌려준다.
     *
     * <p>파일 이름은 구현체가 UUID로 만든다. 사용자가 올린 파일명은 경로 생성에 쓰지 않는다.
     * 확장자는 호출자가 검증을 마친 값을 넘긴다. Storage가 사용자 입력을 직접 해석하지 않게 하기 위함이다.
     *
     * @param sessionId 세션 내부 식별자
     * @param file      업로드된 파일
     * @param extension 확장자 (점 없이, 예: {@code pdf})
     * @throws com.naeil.study.storage.exception.FileStorageException 저장에 실패한 경우
     */
    StoredFile save(UUID sessionId, MultipartFile file, String extension);

    /**
     * 저장된 파일을 삭제한다. 파일이 이미 없으면 아무 일도 하지 않는다.
     *
     * @param storagePath {@link StoredFile#storagePath()}
     */
    void delete(String storagePath);

    /**
     * 저장된 파일을 읽는다. 4단계 파서가 이 메서드로 원본을 다시 읽는다.
     *
     * <p>호출자가 스트림을 닫아야 한다.
     */
    InputStream load(String storagePath);
}
