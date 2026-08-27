package com.naeil.study.storage;

/**
 * Storage에 저장된 파일의 위치 정보.
 *
 * @param storedFileName Storage 내부 파일 이름 (UUID 기반). 사용자 입력 파일명을 쓰지 않는다.
 * @param storagePath    Storage root 기준 상대 경로. DB에 저장하고 이후 파일을 다시 읽을 때 쓴다.
 */
public record StoredFile(String storedFileName, String storagePath) {
}
