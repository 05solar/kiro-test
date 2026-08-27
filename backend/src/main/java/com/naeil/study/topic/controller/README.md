# topic/controller

| 클래스 | 역할 |
| --- | --- |
| `TopicController` | `/api/sessions/{sessionCode}/topics` 매핑 |

## 엔드포인트

| 메서드 | 경로 | 상태 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/sessions/{sessionCode}/topics` | 200 | Topic 목록 (topicOrder 순) |

명세: `docs/api/analysis-api.md` (저장소 루트 기준)

## 상세 조회 API가 없다

목록 응답에 제목, 요약, 핵심 개념, 중요도, 학습시간, 맥락 일치 여부가 모두 들어 있다.
Topic은 세션당 최대 30개라 목록 응답이 커질 위험도 낮다.
필요하지 않은 엔드포인트를 만들지 않는다.

## 분석 진행 상태 API가 없다

`GET /api/sessions/{sessionCode}` 의 `status` 로 확인한다.
