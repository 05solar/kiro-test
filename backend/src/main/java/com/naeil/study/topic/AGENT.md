# topic — 에이전트 작업 규칙

## 지킬 것

- `importance` 를 "출제 확률"로 설명하거나 그렇게 계산하지 않는다. 학습 우선순위다.
- `estimatedStudyMinutes` 를 남은 학습 시간에 맞춰 줄이지 않는다. 커리큘럼 단계의 몫이다.
- `mustStudyMatched` 를 다른 matched 값과 같은 가중치로 다루지 않는다. 제약이다.
- Topic 조회는 세션 ID 기준으로 한다.
- JSON 컬럼에 `columnDefinition` 을 적지 않는다.

## 하지 말 것

- Topic 수정 API 추가 (분석 결과는 통째로 교체한다)
- Topic 상세 조회 API 추가 (목록에 모든 정보가 있다)
- 응답에 `sourceDocumentIds` 노출 (내부 추적 정보다)
- 이 패키지에서 AI 호출
- 세션 범위를 벗어나는 Topic 조회
