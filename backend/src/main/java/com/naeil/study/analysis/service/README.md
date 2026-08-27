# analysis/service

| 클래스 | 역할 |
| --- | --- |
| `AnalysisService` | 분석 오케스트레이션 (트랜잭션 없음) |
| `AnalysisStateWriter` | DB 상태 변경 (짧은 트랜잭션) |
| `AnalysisTarget` | 분석에 필요한 값만 담은 스냅샷 |

## 트랜잭션을 나눈 이유

AI 호출은 수십 초가 걸릴 수 있다. 그 시간 동안 트랜잭션을 잡고 있으면 커넥션이 묶인다.

```
beginAnalysis()    tx  검증 → ANALYZING → 분석 대상 복사
     ↓
(트랜잭션 밖)          Chunking + AI 호출 + 응답 검증
     ↓
completeAnalysis() tx  기존 Topic 삭제 + 새 Topic 저장 + READY
failAnalysis()     tx  ANALYSIS_FAILED
```

`AnalysisService` 가 자기 메서드를 호출하면 프록시를 거치지 않아 `@Transactional` 이
적용되지 않는다. 그래서 상태 변경만 별도 빈으로 분리했다.

## AnalysisTarget

엔티티를 트랜잭션 밖으로 들고 나가지 않는다. 필요한 값만 복사한다.
문서마다 AI용 참조값(`DOC_1`)을 붙여 두어 이후 단계에서 UUID를 다시 만질 일이 없게 한다.

## 실패 처리

어느 시점에 실패하든 `ANALYSIS_FAILED` 로 끝난다.

```java
try { runAnalysis(target); }
catch (RuntimeException e) { stateWriter.failAnalysis(sessionId); throw e; }
```

시작 조건 검증 실패(시험 정보 없음 등)는 `beginAnalysis` 에서 나므로
이 catch에 걸리지 않는다. 상태를 `ANALYZING` 으로 바꾸기 전이기 때문이다.

## 한 조각이 실패하면 전체가 실패한다

일부 자료가 빠진 채 만들어진 커리큘럼은 사용자에게 더 나쁘다.
실패를 감추는 것보다 다시 시도하게 하는 편이 낫다.

## 검증 실패 시 1회 재요청

형식이 어긋나는 응답은 다시 받으면 고쳐지는 경우가 있다.
두 번째도 실패하면 그대로 둔다. 무한 재시도는 비용만 늘린다.
