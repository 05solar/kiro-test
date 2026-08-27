# curriculum/dto

| 클래스 | 방향 | 쓰이는 곳 |
| --- | --- | --- |
| `CurriculumResponse` | 응답 | 계획 전체 |
| `StudyStepResponse` | 응답 | 계획 조회의 단계 하나 |
| `CurriculumProgressResponse` | 응답 | 진행률 |
| `StudyStepProgressResponse` | 응답 | 시작 / 완료 API의 단계 |
| `StepCompletionResponse` | 응답 | 완료 단계 + 다음 단계 |

## 두 시간을 함께 내려준다

```json
{ "originalEstimatedMinutes": 55, "allocatedMinutes": 43 }
```

원래 55분짜리인데 43분만 배정했다는 사실을 화면에서 보여줄 수 있어야
사용자가 시간이 부족하다는 것을 안다. 배정 시간만 주면 그 정보가 사라진다.

계획 수준에서도 같다.

```json
{ "initialRemainingMinutes": 180, "totalAllocatedMinutes": 180 }
```

둘의 차이가 계획에 쓰지 않고 남긴 시간이다.

## REVIEW 단계는 비어 있는 필드가 있다

topicId 와 importance 가 null 이다. Topic에 묶이지 않는 단계이기 때문이다.
키를 생략하지 않고 null 로 내려보낸다.

## 진행 정보를 계획 조회에 함께 담는다

각 단계에 `status` / `startedAt` / `completedAt` / `actualStudyMinutes` 가 들어 있어,
다른 기기에서 세션 코드만 입력해도 이 응답 하나로 학습 화면을 복구할 수 있다.
"현재 단계 조회" API를 따로 만들지 않은 이유다.

## 진행률을 저장하지 않는다

`CurriculumProgressResponse.from(steps)` 가 단계 목록에서 매번 센다.
DB에 `progress = 62` 같은 값을 두면 단계 상태와 어긋날 수 있고,
그때 어느 쪽이 맞는지 판단할 근거가 없다. 기준은 항상 단계의 상태 하나다.

복습 단계도 전체에 포함한다. 사용자가 화면에서 보는 단계 수와 같아야 한다.
`percentage` 는 반올림한 정수다.

## 응답 DTO를 둘로 나눈 이유

```
StudyStepResponse          계획 조회 — 중요도, 선정 이유, 권장 시간까지
StudyStepProgressResponse  시작/완료 — 남은 시간과 지금 상태만
```

진행 중 화면에 "이 단계가 왜 선정되었는가"는 필요 없다.
또한 진행용 DTO는 Topic을 읽지 않아 지연 로딩을 신경 쓸 필요가 없다.
