# studycontext/entity

| 클래스 | 역할 |
| --- | --- |
| `StudyContext` | 학습 맥락 엔티티 |
| `StudyContextPolicy` | 입력 정규화 규칙과 길이 제한 |

## StudyContext

```
id                 UUID
studySession       @OneToOne(LAZY), FK session_id (UNIQUE), 단방향
professorEmphasis  TEXT nullable
pastExamInfo       TEXT nullable
weakAreas          TEXT nullable
mustStudyAreas     TEXT nullable
createdAt / updatedAt
```

| 메서드 | 설명 |
| --- | --- |
| `create(session, ...4, now)` | 최초 저장 |
| `update(...4, now)` | 네 항목 전체 교체 |
| `isEmpty()` | 네 항목이 모두 비었는지 |

세터는 없다. `update()` 는 부분 수정이 아니라 전체 교체다. API가 PUT인 것과 같은 의미다.

## StudyContextPolicy

```
MAX_FIELD_LENGTH   2000
normalize(value)   앞뒤 공백 제거, 공백만 남으면 null
```

## TEXT 컬럼에 @Lob 을 붙이지 않는다

PostgreSQL에서 `@Lob String` 은 JDBC 드라이버가 large object로 저장해
컬럼에 본문 대신 OID 숫자가 들어간다. H2에서는 정상 동작해 테스트로는 드러나지 않는다.
`columnDefinition = "TEXT"` 만으로 길이 제한 없는 컬럼이 만들어진다.

## isEmpty() 가 필요한 이유

이후 AI 프롬프트를 만들 때 맥락 절을 통째로 넣을지 뺄지 판단해야 한다.
비어 있는 맥락을 프롬프트에 넣으면 토큰만 쓰고 판단을 흐린다.
