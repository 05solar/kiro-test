# document/repository

| 인터페이스 | 역할 |
| --- | --- |
| `DocumentRepository` | `JpaRepository<Document, UUID>` |

## 메서드

```java
List<Document>     findAllByStudySessionIdOrderByCreatedAtAsc(UUID sessionId);
Optional<Document> findByIdAndStudySessionId(UUID documentId, UUID sessionId);
long               countByStudySessionId(UUID sessionId);
long               sumFileSizeByStudySessionId(UUID sessionId);   // @Query + coalesce
```

| 메서드 | 쓰는 곳 |
| --- | --- |
| `findAllBy...OrderByCreatedAtAsc` | 목록 조회 (업로드 순서) |
| `findByIdAndStudySessionId` | 삭제. **다른 세션 접근 차단의 핵심** |
| `countByStudySessionId` | 파일 개수 제한 검사 |
| `sumFileSizeByStudySessionId` | 세션 총 용량 제한 검사. 파일이 없으면 0 |

## findByIdAndStudySessionId 를 쓰는 이유

문서 ID만으로 조회한 뒤 소유자를 비교하면, 비교를 빠뜨리는 순간 다른 세션의 문서가 노출된다.
조건을 쿼리에 넣어 두면 그런 실수 자체가 불가능해진다.
