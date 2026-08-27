# curriculum/planner 작업 절차

## 정책을 바꿀 때

```
1. CurriculumPolicy 의 상수나 Comparator 만 고친다
2. CurriculumPlannerTest 에 명시적인 케이스를 추가한다
3. 무작위 입력 테스트가 여전히 통과하는지 확인한다
4. docs/api/curriculum-api.md 의 정책 표를 갱신한다
```

## 테스트 두 종류

```
결정적 테스트   "취약 영역이 덜 깎이는가" 처럼 정책을 확인한다
무작위 테스트   어떤 조합에서도 제약이 깨지지 않는지 확인한다
```

무작위 테스트만으로는 정책이 맞는지 알 수 없고, 결정적 테스트만으로는 놓친 조합을 못 잡는다.
둘 다 필요하다.

## STEP 9 에서 추가할 것

```java
CurriculumPlan reallocate(int remainingMinutes, List<PlannedStep> pendingSteps, ...);
```

완료된 단계는 건드리지 않고 `PENDING` 단계의 배정 시간만 다시 나눈다.
지금 만들지 않는다. 필요해질 때 입력 형태를 보고 정한다.

## 검증

```bash
./gradlew test --tests "*CurriculumPlannerTest*"
```
