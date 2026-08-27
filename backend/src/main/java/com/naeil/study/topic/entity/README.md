# topic/entity

| 클래스 | 역할 |
| --- | --- |
| `Topic` | AI 분석으로 만들어진 학습 단위 |
| `TopicImportance` | 학습 우선순위 Enum |

## Topic

```
id                        UUID
studySession              @ManyToOne(LAZY), FK session_id (단방향)
title                     이름 (문장 아님), 200자
summary                   TEXT
keyPoints                 JSON 배열
importance                VERY_HIGH / HIGH / MEDIUM / LOW
estimatedStudyMinutes     5~120, 조정 전 값
professorEmphasisMatched  / pastExamMatched / weakAreaMatched / mustStudyMatched
sourceDocumentIds         JSON 배열 (문서 UUID)
topicOrder                기본 학습 순서 (1부터)
createdAt / updatedAt
```

세터는 없다. 생성은 `Topic.create(...)` 정적 팩터리로만 한다.
검증을 마친 값만 넘어온다는 전제이므로 엔티티에서 다시 검사하지 않는다.

## TopicImportance

```
VERY_HIGH  반드시 우선적으로 학습해야 할 핵심 내용
HIGH       높은 우선순위
MEDIUM     시간이 있다면 학습해야 하는 내용
LOW        시간이 부족하면 줄일 수 있는 세부 내용
```

`from(String)` 은 AI가 돌려준 문자열을 값으로 바꾼다. 앞뒤 공백과 대소문자는 관대하게
처리하고, 목록에 없는 값은 비어 있는 `Optional` 로 돌려준다.
관대함의 범위를 여기 한 곳에 두어 검증기가 판단을 중복하지 않게 한다.

## JSON 컬럼

`@JdbcTypeCode(SqlTypes.JSON)` 만 붙이고 `columnDefinition` 은 적지 않는다.
PostgreSQL은 `jsonb`, H2는 `json` 으로 Dialect가 고른다.
