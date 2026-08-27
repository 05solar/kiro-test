# storage — 에이전트 작업 규칙

## 지킬 것

- 도메인 서비스는 `StorageService` 만 안다. `Files.copy`, S3 SDK 를 도메인 코드에서 직접 부르지 않는다.
- 저장 파일명은 UUID로 만든다. 사용자가 올린 파일명을 경로에 넣지 않는다.
- 경로에 세션 코드를 쓰지 않는다. 내부 UUID(`StudySession.id`)를 쓴다.
- 모든 경로는 root 기준으로 정규화하고, root를 벗어나면 거부한다.
- 저장소 오류는 `FileStorageException` 으로 감싼다. 서버 경로와 OS 메시지는 로그에만 남긴다.

## 하지 말 것

- 응답에 `storagePath` / `storedFileName` 노출
- 파일 다운로드 API 추가 (이번 MVP 범위가 아니다)
- Storage에 도메인 지식(Document, 세션 상태) 주입
- 파일 내용을 로그로 출력
