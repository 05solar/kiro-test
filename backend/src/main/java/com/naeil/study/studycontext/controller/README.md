# studycontext/controller

| 클래스 | 역할 |
| --- | --- |
| `StudyContextController` | `/api/sessions/{sessionCode}/study-context` 매핑 |

## 엔드포인트

| 메서드 | 경로 | 상태 | 설명 |
| --- | --- | --- | --- |
| PUT | `/api/sessions/{sessionCode}/study-context` | 200 | 저장 / 수정 (Upsert) |
| GET | `/api/sessions/{sessionCode}/study-context` | 200 | 조회 |

명세: `docs/api/study-context-api.md` (저장소 루트 기준)

## PUT을 쓰는 이유

없으면 만들고 있으면 전체를 교체한다. 부분 수정이 아니므로 PATCH가 아니다.
보내지 않은 항목은 비워진다.

## 조회에 404가 없다

학습 맥락을 입력하지 않은 세션도 200에 전부 `null` 로 응답한다.
선택 입력이라 "없음"이 정상 상태이고, 이 응답을 그대로 입력 화면 초기값으로 쓸 수 있다.

`Optional` 을 응답으로 바꾸는 곳이 여기다. 서비스는 `Optional` 을 그대로 돌려준다.

## 삭제 API가 없다

네 항목을 모두 `null` 로 PUT하면 같은 효과가 난다. 엔드포인트를 하나 더 만들 이유가 없다.
