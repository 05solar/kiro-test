# curriculum/entity — 에이전트 작업 규칙

## 지킬 것

- 세터를 만들지 않는다. 생성은 정적 팩터리로만 한다.
- 시각은 파라미터로 받는다. 엔티티 안에서 `LocalDateTime.now()` 를 부르지 않는다.
- `originalEstimatedMinutes` 와 `initialRemainingMinutes` 는 `updatable = false` 를 유지한다.
- JSON 컬럼에 `columnDefinition` 을 적지 않는다.
- 상태 전이 메서드를 더할 때 잘못된 전이를 예외로 막는다.

## 하지 말 것

- 시간 값 세 개를 하나로 합치기
- `Curriculum` 에 `List<StudyStep>` 추가 (단방향을 유지한다)
- `REVIEW` 단계에 Topic 연결을 강제하기
- `@Enumerated(ORDINAL)` 사용
- 엔티티에서 시간 배분 계산 (Planner 의 몫이다)

## 상태 전이 메서드

```
StudyStep.start / complete
Curriculum.startProgress / completeProgress
StudySession.startStep / clearCurrentStep
```

- 잘못된 전이는 예외로 막는다. 조용히 무시하지 않는다.
- `Curriculum.startProgress` 와 `StudySession.startStep` 은 되돌리지 않는다.
  이미 진행 중이거나 끝난 것을 앞 상태로 되돌리면 기록이 어긋난다.
- `complete` 는 `actualStudyMinutes` 를 **올림**으로 계산한다. 버리면 40초 학습이 0분이 된다.
- 실제 학습시간을 `allocatedMinutes` 로 자르지 않는다.
- `COMPLETED` 단계의 시간 값을 다시 계산하지 않는다. 멱등 처리는 서비스가 한다.
