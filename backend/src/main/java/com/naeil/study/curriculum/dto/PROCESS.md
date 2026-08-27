# curriculum/dto 작업 절차

## 필드를 추가할 때

```
1. 화면에서 실제로 쓰는 값인지 판단한다
2. record 에 필드 추가 + from() 매핑
3. REVIEW 단계에서 null 이 되는 값인지 확인한다
4. CurriculumControllerTest 에 JSON 키 검증 추가
5. docs/api/curriculum-api.md 예시 JSON 갱신
```

## 연관관계를 읽는 필드를 추가할 때

응답 변환은 트랜잭션 밖에서 일어난다. 지연 로딩 연관관계를 새로 읽는다면
리포지터리 쿼리에 페치 조인을 함께 추가해야 한다.

## 검증

```bash
./gradlew test --tests "*CurriculumControllerTest*" --tests "*CurriculumApiIntegrationTest*"
```

## STEP 8 에서 한 일

```
StudyStepResponse           actualStudyMinutes / startedAt / completedAt 추가
CurriculumResponse          progress 추가
CurriculumProgressResponse  신규 — 단계 목록에서 계산
StudyStepProgressResponse   신규 — 시작/완료 응답
StepCompletionResponse      신규 — 완료 단계 + 다음 단계
```

계획 조회 응답이 진행 상태까지 담게 되어 "현재 단계 조회" API가 필요 없어졌다.

진행용 DTO를 따로 둔 이유는 Topic을 읽지 않아도 되기 때문이다.
`StudyStepResponse` 는 `topic.getImportance()` 를 읽어 페치 조인이 필요하지만,
`StudyStepProgressResponse` 는 단계 자신의 값만 쓴다.
