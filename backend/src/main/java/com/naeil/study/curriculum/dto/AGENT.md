# curriculum/dto — 에이전트 작업 규칙

## 지킬 것

- record 로 만든다. 변환은 정적 팩터리 from() / of() 에 둔다.
- originalEstimatedMinutes 와 allocatedMinutes 를 함께 내려준다.
- REVIEW 단계의 빈 값은 키를 유지한 채 null 로 내려보낸다.

## 하지 말 것

- 배정 시간만 노출하고 권장 시간 감추기
- @JsonInclude(NON_NULL) 추가 (REVIEW 단계의 키가 사라진다)
- 엔티티를 그대로 반환
- DTO 에서 시간 계산

## 학습 진행 (STEP 8)

- 실제 학습시간을 요청 본문으로 받지 않는다. 서버가 계산한다.
- 단계 완료 시 `remainingStudyMinutes` 와 남은 단계의 `allocatedMinutes` 를 건드리지 않는다.
- 단계 조회는 항상 `stepId` + 계획 id 로 한다. 없으면 404다 (403이 아니다).
- `IN_PROGRESS → start`, `COMPLETED → complete` 는 오류가 아니라 기존 결과 반환이다.
