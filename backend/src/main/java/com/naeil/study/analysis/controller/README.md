# analysis/controller

| 클래스 | 역할 |
| --- | --- |
| `AnalysisController` | `/api/sessions/{sessionCode}/analysis` 매핑 |

## 엔드포인트

| 메서드 | 경로 | 상태 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/sessions/{sessionCode}/analysis` | 200 | 분석 실행 (동기) |

명세: `docs/api/analysis-api.md` (저장소 루트 기준)

## Request Body가 없다

분석에 필요한 강의자료, 추출 텍스트, 학습 맥락은 이미 DB에 있다.
프론트가 텍스트를 다시 올려보내면 대역폭만 쓰고, 서버가 가진 값과 어긋날 수도 있다.

## 200을 돌려주는 이유

동기로 처리하므로 응답 시점에 분석이 끝나 있다.
진행 중 상태가 아니라 최종 상태(`READY`)와 Topic 수를 돌려준다.

비동기로 옮기면 202와 `ANALYZING` 을 돌려주게 된다.
그때 손대는 곳은 이 컨트롤러와 서비스 호출 지점뿐이다.

## 분석 진행 상태 API가 없다

`GET /api/sessions/{sessionCode}` 의 `status` 로 확인한다. 중복 API를 만들지 않는다.

가짜 진행률(%)도 만들지 않는다. AI 작업의 실제 진행도를 알 수 없다.
