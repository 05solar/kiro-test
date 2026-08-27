# topic/service

| 클래스 | 역할 |
| --- | --- |
| `TopicService` | Topic 조회 |

## 메서드

| 메서드 | 설명 |
| --- | --- |
| `findAll(sessionCode)` | 세션의 Topic을 topicOrder 순으로 조회 |

## 읽기만 담당한다

Topic을 만드는 것은 `analysis` 도메인이다. 이 서비스에 생성이나 수정 메서드를 두지 않는다.
분석 흐름이 여기를 거치면 트랜잭션 경계가 두 도메인에 걸쳐 흐려진다.

## 세션 접근

세션은 `SessionService.getSessionAndTouch()` 로 가져온다.
Topic 조회도 세션 활동이므로 접근시각과 보관기한이 갱신된다.

아직 분석하지 않은 세션은 빈 목록을 돌려준다. 오류가 아니다.
