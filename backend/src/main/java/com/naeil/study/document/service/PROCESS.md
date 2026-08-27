# document/service 작업 절차

## 유스케이스를 추가할 때

```
1. 세션 조회는 SessionService 를 통한다
2. 파일 시스템을 건드리는 작업에는 반드시 실패 경로를 먼저 설계한다
   - Storage 성공 + DB 실패 → 보상 삭제
   - DB 성공 + Storage 실패 → 무엇이 남는가?
3. 쓰기 메서드에 @Transactional (클래스는 readOnly = true)
4. 테스트: 정상 경로 + 실패 경로 + 보상 처리 확인
```

## 테스트 작성 방식

StorageService 와 DocumentRepository 를 목으로 두고 보상 처리를 검증한다.

```java
given(documentRepository.saveAllAndFlush(anyList()))
        .willThrow(new DataIntegrityViolationException("boom"));
// verify(storageService, times(2)).delete(anyString());
```

세션 id 는 JPA가 채우므로 단위 테스트에서는 리플렉션으로 넣는다.

## STEP 4-2 에서 추가할 것

```
parse(sessionCode)
  1. UPLOADED 상태 문서 조회
  2. 문서별 status = PARSING
  3. storageService.load(storagePath) 로 원본 읽기
  4. 형식별 Parser 실행
  5. 성공 → PARSED + extractedText / 실패 → PARSE_FAILED
```

파싱은 오래 걸리므로 트랜잭션 안에서 전부 처리하지 않는다. 문서 단위로 나눈다.

## 검증

```bash
./gradlew test --tests "*DocumentServiceTest*"
```
