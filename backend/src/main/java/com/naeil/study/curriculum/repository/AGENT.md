# curriculum/repository — 에이전트 작업 규칙

## 지킬 것

- 단계 조회는 curriculumId 조건과 함께 한다.
- 응답에서 읽는 연관관계는 페치 조인한다.
- REVIEW 단계가 빠지지 않도록 left join fetch 를 쓴다.
- 정렬 없는 단계 조회를 만들지 않는다. 순서가 의미를 갖는다.

## 하지 말 것

- 세션/계획 범위를 벗어나는 전역 조회
- findById(stepId) 만으로 단계를 가져와 소유자를 나중에 비교
- 쓰지 않는 메서드 미리 추가

## 학습 진행 (STEP 8)

- 실제 학습시간을 요청 본문으로 받지 않는다. 서버가 계산한다.
- 단계 완료 시 `remainingStudyMinutes` 와 남은 단계의 `allocatedMinutes` 를 건드리지 않는다.
- 단계 조회는 항상 `stepId` + 계획 id 로 한다. 없으면 404다 (403이 아니다).
- `IN_PROGRESS → start`, `COMPLETED → complete` 는 오류가 아니라 기존 결과 반환이다.
