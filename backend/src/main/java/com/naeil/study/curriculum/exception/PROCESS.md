# curriculum/exception 작업 절차

## 새 예외를 추가하는 순서

```
1. common/exception/ErrorCode 에 항목 추가 (HTTP 상태 + 한국어 메시지)
2. 여기에 BusinessException 하위 클래스 생성
3. 서비스나 Planner 에서 throw
4. 테스트: 서비스 단위 + 컨트롤러(상태 코드, code 문자열)
5. docs/api/error-codes.md 와 docs/api/curriculum-api.md 갱신
```

## 400인지 422인지 정할 때

```
사용자가 앞 단계를 마치면 해결되는가?  → 400
요청은 올바른데 만들 수 없는가?        → 422
```

메시지에 무엇을 하면 되는지 적는다. 422는 할 수 있는 일이 없을 수 있으므로
상황을 있는 그대로 알린다.

## 검증

```bash
./gradlew test --tests "*CurriculumControllerTest*"
```

## STEP 8 에서 추가한 예외

```
StudyStepNotFoundException           404
StudyStepAlreadyCompletedException   409
StudyStepNotStartedException         409
InvalidStudyStepOrderException       409
AnotherStepInProgressException       409
ExamAlreadyStartedException          409
```

`ExamAlreadyStartedException` 은 시험 시각에 관한 것이라 `session/exception` 에 둘까
고민했지만, 학습 단계 시작에서만 의미가 있어 여기에 두었다.
세션 조회나 시험 정보 수정은 시험이 지났다고 막지 않는다.

`StudyStep.start` / `complete` 가 이 예외들을 직접 던진다.
서비스가 앞서 걸러 내므로 실제로는 거의 걸리지 않지만, 상태 규칙이 엔티티 밖으로
새어 나가지 않게 하려면 판단도 엔티티가 해야 한다.
