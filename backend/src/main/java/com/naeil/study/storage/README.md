# storage

파일 저장소 추상화. 도메인 코드가 저장 방식을 모르게 한다.

| 클래스 | 역할 |
| --- | --- |
| `StorageService` | 저장 / 삭제 / 읽기 인터페이스 |
| `LocalStorageService` | 로컬 파일 시스템 구현 (개발/MVP) |
| `StoredFile` | 저장 결과 (실제 파일명, 상대 경로) |
| `exception/FileStorageException` | 저장소 오류 → 500 |

## 인터페이스

```java
StoredFile   save(UUID sessionId, MultipartFile file, String extension);
void         delete(String storagePath);
InputStream  load(String storagePath);
```

`load`는 3단계에서 아직 호출하는 곳이 없다. 4단계 파서가 저장된 원본을 다시 읽어야 하므로
Storage 계약에 함께 넣어 두었다. 파일 다운로드 API는 만들지 않는다.

## 저장 경로

```
{root}/sessions/{sessionId}/documents/{uuid}.{ext}
```

- 경로에 **세션 코드를 쓰지 않는다.** 코드가 곧 접근 키라서 파일 경로에 남기지 않는다.
- 파일명은 UUID로 만든다. 사용자가 올린 이름은 경로 생성에 관여하지 않는다.
- 확장자는 검증을 마친 값을 호출자가 넘긴다. Storage가 사용자 입력을 해석하지 않는다.

## 경로 이탈 방지

`resolve()`가 정규화한 결과를 root와 대조한다. root를 벗어나면 거부한다.
저장 경로는 서버가 만들지만, DB에 잘못된 값이 들어간 경우에도 root 밖 파일을 건드리지 못하게 한다.

## 설정

```yaml
storage:
  local:
    root-path: ${STORAGE_ROOT_PATH:./uploads}
```

배포 시 이 패키지에 S3 호환 구현을 추가하고 빈만 교체한다. 도메인 코드는 바뀌지 않는다.
