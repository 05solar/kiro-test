# studycontext

사용자가 AI 분석에 덧붙이는 학습 맥락.

```
studycontext
├── controller/   PUT / GET API
├── service/      Upsert + 조회
├── repository/   Spring Data JPA
├── entity/       StudyContext, StudyContextPolicy
└── dto/          요청/응답 record
```

## 네 가지 질문

| 필드 | 화면 질문 | 이후 용도 |
| --- | --- | --- |
| `professorEmphasis` | 교수님이 강조한 부분이 있나요? | Topic 우선순위 상향 |
| `pastExamInfo` | 기출/예상 문제가 있나요? | Topic 우선순위 + Quiz 유형 비중 |
| `weakAreas` | 자신 없는 부분은 무엇인가요? | 학습시간 / Quiz / 복습 비중 |
| `mustStudyAreas` | 반드시 공부하고 싶은 범위가 있나요? | **제약**. 시간이 부족해도 빼지 않는다 |

이번 단계에서는 저장과 조회만 한다. 가중치 계산은 하지 않는다.

## 핵심 개념

### 세션당 하나다

`session_id` 에 UNIQUE 를 걸어 두 개가 생길 수 없게 했다.
저장은 Upsert다. 없으면 만들고 있으면 고친다.

세션당 학습 맥락이 둘 이상이면 어느 것이 최신인지 코드가 판단해야 한다.
제약으로 막으면 그 판단 자체가 필요 없어진다.

### 전부 선택 입력이다

네 항목 모두 비어 있어도 정상이다. 하나도 입력하지 않아도 이후 AI 기능은 동작해야 한다.
그래서 조회는 값이 없어도 404가 아니라 200에 전부 `null` 로 응답한다.

### PUT은 전체 교체다

보내지 않은 항목은 비워진다. 그래서 삭제 API가 없다.
네 항목을 모두 `null` 로 PUT하면 같은 효과가 난다.

### 정규화

```
"   교착상태 강조   "  →  "교착상태 강조"
"     "               →  null
```

빈 문자열과 `null` 이 섞이면 "입력하지 않음" 판단 조건이 여기저기서 달라진다.
저장 시점에 하나로 통일한다.

### 사용자 입력은 검증된 사실이 아니다

`pastExamInfo` 에 "교착상태가 100% 나온다"가 들어와도 시스템이 사실로 확정하면 안 된다.
이후 AI 프롬프트에서 시스템 지시문과 사용자 제공 맥락을 명확히 분리한다.
학습 맥락을 시스템 프롬프트 문자열에 그대로 이어붙이는 구조는 만들지 않는다.

## 구현된 API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| PUT | `/api/sessions/{sessionCode}/study-context` | 저장 / 수정 (Upsert) |
| GET | `/api/sessions/{sessionCode}/study-context` | 조회 (없으면 200 + null) |

명세: `docs/api/study-context-api.md` (저장소 루트 기준)

## 아직 없는 것

```
가중치 계산                STEP 6 이후
Prompt Injection 필터링    AI 연동 시 검토
기출 파일 업로드            MVP 제외 (텍스트 입력만)
DELETE API                 만들지 않는다
```
