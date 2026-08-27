# curriculum/exception

| 예외 | HTTP | code |
| --- | --- | --- |
| `SessionNotReadyException` | 400 | `SESSION_NOT_READY` |
| `TopicsRequiredException` | 400 | `TOPICS_REQUIRED` |
| `NoStudyTimeAvailableException` | 400 | `NO_STUDY_TIME_AVAILABLE` |
| `CurriculumNotFoundException` | 404 | `CURRICULUM_NOT_FOUND` |
| `CurriculumGenerationFailedException` | 422 | `CURRICULUM_GENERATION_FAILED` |
| `StudyStepNotFoundException` | 404 | `STUDY_STEP_NOT_FOUND` |
| `StudyStepAlreadyCompletedException` | 409 | `STUDY_STEP_ALREADY_COMPLETED` |
| `StudyStepNotStartedException` | 409 | `STUDY_STEP_NOT_STARTED` |
| `InvalidStudyStepOrderException` | 409 | `INVALID_STUDY_STEP_ORDER` |
| `AnotherStepInProgressException` | 409 | `ANOTHER_STEP_IN_PROGRESS` |
| `ExamAlreadyStartedException` | 409 | `EXAM_ALREADY_STARTED` |

## 400과 422를 나눈 이유

```
400  사용자가 앞 단계를 마치면 해결된다 (분석하기, 시험 정보 확인하기)
422  요청은 올바른데 그 조건으로는 만들 수 있는 계획이 없다
```

남은 시간이 3분이면 사용자가 뭘 해도 5분짜리 학습 단계를 만들 수 없다.
그건 잘못된 요청이 아니라 처리할 수 없는 상태다.

## CurriculumGenerationFailedException 의 reason

로그와 진단용 내부 요약이다. 사용자 응답에는 나가지 않는다.

```
available minutes below minimum: 3
allocated exceeds available: 200 > 180
duplicated topic in plan: ...
```

계획이 지켜야 할 조건이 깨졌을 때도 이 예외를 쓴다.
조용히 잘못된 계획을 내보내는 것보다 실패하는 편이 낫다.

## 학습 진행에서 409를 쓰는 이유

```
400  요청 값이 잘못됐다
409  요청은 올바른데 지금 상태와 충돌한다
```

완료한 단계를 다시 시작하는 요청, 앞선 단계를 건너뛰는 요청, 시험 시각이 지난 뒤의
시작 요청은 모두 형식이 올바르다. 충돌하는 것은 서버가 가진 현재 상태다.

`EXAM_ALREADY_STARTED` 를 400이 아니라 409로 둔 것도 같은 이유다.
사용자가 값을 잘못 넣은 것이 아니라 시간이 지났을 뿐이다.

## STUDY_STEP_NOT_FOUND 는 403이 아니다

다른 세션의 단계에 403을 주면 "그 단계는 존재한다"는 사실이 드러난다.
세션 코드가 유일한 접근 키이므로 존재 여부 자체를 알려주지 않는다.

## 멱등한 경우는 예외가 아니다

이미 진행 중인 단계에 start, 이미 완료한 단계에 complete 는 오류로 다루지 않는다.
기존 상태를 그대로 돌려준다. 버튼을 두 번 누른 사용자에게 오류 화면을 보여줄 이유가 없다.
