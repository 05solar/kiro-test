# curriculum/controller — 에이전트 작업 규칙

## 지킬 것

- 컨트롤러는 얇게 유지한다. 매핑과 변환만 한다.
- 새로 만든 경우에만 201, 기존 계획을 돌려줄 때는 200.
- 모든 경로를 /api/sessions/{sessionCode} 아래에 둔다.

## 하지 말 것

- 계획 재생성 엔드포인트 추가 (정책을 정하기 전까지)
- 시간 배분 파라미터를 요청으로 받기
- 컨트롤러에서 계획 계산
- 실패 응답에 내부 원인(제약 위반 상세) 노출

## 학습 진행 (STEP 8)

- 실제 학습시간을 요청 본문으로 받지 않는다. 서버가 계산한다.
- 단계 완료 시 `remainingStudyMinutes` 와 남은 단계의 `allocatedMinutes` 를 건드리지 않는다.
- 단계 조회는 항상 `stepId` + 계획 id 로 한다. 없으면 404다 (403이 아니다).
- `IN_PROGRESS → start`, `COMPLETED → complete` 는 오류가 아니라 기존 결과 반환이다.
