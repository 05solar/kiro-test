# curriculum/repository 작업 절차

## 조회 메서드를 추가할 때

```
1. 세션이나 계획 범위를 벗어나는 조회를 만들지 않는다
2. 응답 변환에서 읽는 연관관계가 있으면 페치 조인한다
3. 테스트에 다른 세션/계획 데이터를 섞어 두고 격리를 확인한다
```

## STEP 8 에서 필요할 것

```java
Optional<StudyStep> findByIdAndCurriculumId(UUID stepId, UUID curriculumId);
```

문서 삭제와 같은 이유로, 단계 조회에도 상위 식별자를 함께 조건으로 건다.

## 검증

```bash
./gradlew test --tests "*CurriculumRepositoryTest*"
```

## STEP 8 에서 한 일

```java
Optional<StudyStep> findByIdAndCurriculumId(UUID id, UUID curriculumId);
Optional<StudyStep> findFirstByCurriculumIdAndStatusOrderByStepOrderAsc(
        UUID curriculumId, StudyStepStatus status);
```

메서드 이름으로 끝나는 조회라 `@Query` 를 쓰지 않았다.

`countByCurriculumIdAndStatus` 도 만들려다 지웠다. 진행률은 이미 로딩한 단계 목록에서
세면 되고, 쿼리를 두 번 더 날릴 이유가 없다. 실제로 쓰는 메서드만 남긴다.

## 검증

```bash
./gradlew test --tests "*CurriculumRepositoryTest*" --tests "*StudyStepApiIntegrationTest*"
```
