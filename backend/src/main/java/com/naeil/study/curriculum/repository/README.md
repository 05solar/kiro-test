# curriculum/repository

| 인터페이스 | 역할 |
| --- | --- |
| `CurriculumRepository` | `JpaRepository<Curriculum, UUID>` |
| `StudyStepRepository` | `JpaRepository<StudyStep, UUID>` |

## 메서드

```java
Optional<Curriculum> findByStudySessionId(UUID sessionId);

@Query("... left join fetch step.topic ...")
List<StudyStep> findAllByCurriculumIdOrderByStepOrderAsc(UUID curriculumId);

Optional<StudyStep> findByIdAndCurriculumId(UUID id, UUID curriculumId);

Optional<StudyStep> findFirstByCurriculumIdAndStatusOrderByStepOrderAsc(
        UUID curriculumId, StudyStepStatus status);
```

## 단계 조회에 페치 조인을 쓰는 이유

응답 변환은 트랜잭션 밖에서 일어나고, 그때 Topic의 중요도를 읽는다.
지연 로딩 상태로 두면 그 시점에 초기화가 필요해져 예외가 난다.
단계 수만큼 추가 조회가 나가는 것도 막는다.

REVIEW 단계는 Topic이 없으므로 left join 이어야 한다.
join fetch 로 쓰면 복습 단계가 결과에서 조용히 사라진다.

## 단계를 id만으로 찾지 않는다

```java
findByIdAndCurriculumId(stepId, curriculum.getId())
```

세션 코드로 찾은 계획에 실제로 속해 있는지 함께 확인한다.
`findById(stepId)` 만 쓰면 다른 세션의 단계를 진행시킬 수 있다.

## findFirst...OrderByStepOrderAsc 를 두 곳에 쓴다

```
PENDING      지금 시작할 수 있는 단계 / 완료 후 다음 단계
IN_PROGRESS  이미 진행 중인 단계가 있는지 확인
```

한 계획의 단계 수는 많아야 수십 개다. 인덱스를 따로 걸지 않았다.
필요해지면 `(curriculum_id, status, step_order)` 복합 인덱스를 검토한다.
