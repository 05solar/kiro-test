# curriculum/service — 에이전트 작업 규칙

## 지킬 것

- 생성자 주입만 쓴다.
- 세션은 SessionService 를 통해 가져온다.
- 기존 계획 확인을 상태 검사보다 먼저 한다.
- 시간 배분은 Planner 에 맡긴다. 서비스에서 계산하지 않는다.
- 시각은 주입받은 Clock 에서 얻는다.
- 남은 학습 시간은 줄이기만 한다. 늘리지 않는다.

## 하지 말 것

- 계획 생성으로 세션 상태를 IN_PROGRESS 로 바꾸기
- currentStepOrder 를 여기서 설정하기 (학습 시작 시점의 일이다)
- 이미 있는 계획을 덮어쓰기
- 정책 숫자를 서비스에 직접 쓰기
- 학습 진행(시작/완료) 로직을 여기에 추가

## 학습 진행 (STEP 8)

- 실제 학습시간을 요청 본문으로 받지 않는다. 서버가 계산한다.
- 단계 완료 시 `remainingStudyMinutes` 와 남은 단계의 `allocatedMinutes` 를 건드리지 않는다.
- 단계 조회는 항상 `stepId` + 계획 id 로 한다. 없으면 404다 (403이 아니다).
- `IN_PROGRESS → start`, `COMPLETED → complete` 는 오류가 아니라 기존 결과 반환이다.
