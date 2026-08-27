# studycontext/service

| 클래스 | 역할 |
| --- | --- |
| `StudyContextService` | 저장(Upsert) / 조회 |

## 메서드

| 메서드 | 설명 |
| --- | --- |
| `upsert(sessionCode, request)` | 없으면 생성, 있으면 수정 |
| `find(sessionCode)` | 조회. 없으면 `Optional.empty()` |

## Upsert 흐름

```
세션 조회 (SessionService)
     ↓
입력값 정규화 (StudyContextPolicy)
     ↓
기존 학습 맥락 조회 (세션 ID 기준)
     ↓
없음 → save()        있음 → update()
     ↓
접근시각/보관기한 갱신 (세션 조회 시 함께 처리됨)
```

새 행을 덧붙이지 않는다. `session_id` UNIQUE 제약이 이를 한 번 더 보장한다.

## 조회 결과가 비어 있는 것은 오류가 아니다

`find()` 는 `Optional` 을 돌려준다. 학습 맥락은 선택 입력이라 "없음"이 정상 상태다.
컨트롤러가 이를 200 + 전부 null 응답으로 바꾼다. 서비스에서 예외를 던지지 않는다.

## 세션 접근

세션은 `SessionService.getSessionAndTouch()` 로 가져온다.
다른 도메인의 Repository를 직접 쓰지 않는다.
조회와 함께 접근시각/보관기한이 갱신되므로 저장·조회 모두 세션 활동으로 기록된다.

세션 상태(`StudySession.status`)는 바꾸지 않는다.
