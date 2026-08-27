# document/service

| 클래스 | 역할 |
| --- | --- |
| `DocumentService` | 업로드 / 목록 조회 / 삭제 |
| `DocumentParsingService` | 텍스트 추출 오케스트레이션 (트랜잭션 없음) |
| `DocumentParseStateWriter` | 파싱 상태 변경 (짧은 트랜잭션) |

## 메서드

| 메서드 | 설명 |
| --- | --- |
| `upload(sessionCode, files)` | 전체 검증 → Storage 저장 → DB 저장 → 세션 상태 전이 |
| `findAll(sessionCode)` | 업로드 순서대로 조회 |
| `delete(sessionCode, documentId)` | DB 삭제 → Storage 삭제 |

## 파일 시스템과 DB는 한 트랜잭션이 아니다

DB는 롤백되지만 이미 저장된 파일은 저절로 사라지지 않는다. 그래서 보상 삭제를 직접 한다.

```
전체 검증 → Storage 저장 → DB 저장(saveAllAndFlush)
                   ↑                 │ 실패
                   └── 보상 삭제 ────┘
```

saveAllAndFlush 를 쓰는 이유는, 기본 saveAll 은 커밋 시점에 DB에 반영되어
예외가 서비스 메서드 밖에서 터지기 때문이다. 그러면 보상 삭제를 할 수 없다.

## 삭제 순서

DB를 먼저 지우고 Storage를 지운다.

```
DB 삭제 (flush) → Storage 삭제
```

반대 순서라면 DB 삭제가 실패했을 때 파일 없는 메타데이터가 남는다.
지금 순서에서는 최악의 경우 참조되지 않는 파일만 남고, 그 파일은 세션 만료 시 함께 정리된다.

## 세션 접근

세션은 SessionService.getSessionAndTouch() 로 가져온다.
다른 도메인의 Repository를 직접 쓰지 않는다.
조회와 함께 접근시각/보관기한이 갱신되므로 업로드·목록·삭제 모두 세션 활동으로 기록된다.
