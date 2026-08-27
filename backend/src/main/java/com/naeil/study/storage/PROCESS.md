# storage 작업 절차

## 새 Storage 구현을 추가할 때

```
1. StorageService 를 구현한다
2. 저장 경로 규칙(sessions/{sessionId}/documents/{uuid}.{ext})을 그대로 유지한다
3. 실패는 FileStorageException 으로 감싼다. SDK 예외를 그대로 올리지 않는다
4. @Profile 또는 조건부 빈으로 구현을 고른다
5. LocalStorageServiceTest 와 같은 시나리오로 새 구현 테스트를 작성한다
```

## 인터페이스를 바꿀 때

`StorageService` 변경은 모든 구현체와 호출자에 영향을 준다.

```
1. 정말 인터페이스에 있어야 하는 기능인지 확인한다
2. 4단계 파서가 필요로 하는 형태인지 확인한다 (load)
3. 전체 테스트를 돌린다
```

## 테스트

`@TempDir` 를 쓴다. 프로젝트 폴더(`./uploads`)에 테스트 파일을 남기지 않는다.

```java
@TempDir Path tempDir;
storageService = new LocalStorageService(tempDir.toString());
```

통합 테스트는 `@DynamicPropertySource` 로 `storage.local.root-path` 를 임시 디렉터리로 돌린다.

## 검증

```bash
./gradlew test --tests "*LocalStorageServiceTest*"
```
