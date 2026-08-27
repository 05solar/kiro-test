# curriculum — 에이전트 작업 규칙

## 절대 지킬 것

- **배정 시간의 합이 가용 시간을 넘지 않는다.** 실행할 수 없는 일정을 내보내지 않는다.
- 시간 배분을 AI에게 맡기지 않는다. 규칙으로 계산하고 코드가 제약을 보장한다.
- `mustStudy` 주제를 중요도가 낮다는 이유로 빼지 않는다.
- 최초 계획에서 배정 시간이 권장 시간을 넘지 않는다.
- 계획을 만든 것만으로 세션을 `IN_PROGRESS` 로 바꾸지 않는다.
- 이미 계획이 있으면 다시 만들지 않는다.

## 시간 값 세 개를 합치지 않는다

```
originalEstimatedMinutes / allocatedMinutes / actualStudyMinutes
```

"어차피 같은 시간 아니냐"는 이유로 줄이면 계획 대비 실제를 비교할 수 없게 되고,
9단계 동적 재조정의 입력이 사라진다.

## 우선순위

- 숫자 점수(`VERY_HIGH = 100`, `교수 강조 = +30`)를 도입하지 않는다.
  근거 없이 정해지고 아무도 왜 그 값인지 설명하지 못한다.
- 순서는 `CurriculumPolicy.priorityComparator()` 한 곳에만 둔다.
- 최종 단계 순서는 `topicOrder` 를 따른다. 중요도로 순서를 바꾸지 않는다.

## 하지 말 것

- 계획 생성 시 AI 호출
- 정책 숫자를 서비스/컨트롤러에 직접 쓰기
- 안전 여유시간을 서버가 임의로 빼기 (사용자 입력이 이미 실제 공부 가능 시간이다)
- 계획 자동 재생성 (정책을 정하기 전까지)
- `COMPLETED` 단계의 시간 값 수정

## 학습 진행에서 지킬 것

- **실제 학습시간은 서버가 계산한다.** 요청 본문으로 `actualStudyMinutes` 를 받지 않는다.
  화면 타이머는 표시용이고, 기준은 `startedAt` 과 완료 요청 시각이다.
- `actualStudyMinutes` 를 `allocatedMinutes` 로 자르지 않는다. 초과분이 재조정의 입력이다.
- 경과 시간은 **올림**한다. `ChronoUnit.MINUTES` 로 버리면 40초 학습이 0분이 되고,
  재조정이 "시간을 쓰지 않았다"고 판단한다.
- 단계 완료 시 `remainingStudyMinutes` 를 차감하지 않는다. 9단계에서 다시 계산한다.
- 완료 시 남은 단계의 `allocatedMinutes` 를 건드리지 않는다.
- 시작 가능한 단계는 `PENDING` 중 가장 앞선 하나뿐이다.
- 한 계획에서 `IN_PROGRESS` 인 단계는 하나 이하다.
- 계획이 끝나도 `StudySession.status` 를 `COMPLETED` 로 바꾸지 않는다. 퀴즈와 최종 복습이 남아 있다.
- `currentStepOrder` 는 **진행 중**인 단계다. 완료하면 `null` 로 비운다.
  완료 직후 다음 순번을 미리 넣지 않는다.

## 멱등하게 둘 것

```
IN_PROGRESS → start     기존 상태 반환. startedAt 을 덮지 않는다
COMPLETED   → complete  기존 결과 반환. 다시 계산하지 않는다
```

409로 막으면 버튼을 두 번 누른 사용자가 오류 화면을 본다.
반대로 다시 계산하면 요청할 때마다 학습시간이 늘어난다.

## 소유권 검증

```java
studyStepRepository.findByIdAndCurriculumId(stepId, curriculum.getId())
```

`findById(stepId)` 만으로 조회하지 않는다. 다른 세션의 단계를 진행할 수 있게 된다.
없을 때는 **404**다. 403을 주면 "그 단계는 존재한다"는 사실이 드러난다.

## 상태 전이 로직의 위치

`StudyStep.start(now)` / `StudyStep.complete(now)` 안에 둔다.
서비스에서 `setStatus` / `setStartedAt` 을 늘어놓지 않는다.
서비스는 순서 / 소유권 / 동시 진행처럼 엔티티 하나로 판단할 수 없는 것만 본다.
