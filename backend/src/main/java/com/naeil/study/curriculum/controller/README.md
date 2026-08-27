# curriculum/controller

| 클래스 | 역할 |
| --- | --- |
| `CurriculumController` | `/api/sessions/{sessionCode}/curriculum` 매핑 |
| `StudyStepController` | `/api/sessions/{sessionCode}/steps` 매핑 |

## 엔드포인트

| 메서드 | 상태 | 설명 |
| --- | --- | --- |
| POST | 201 / 200 | 계획 생성. 이미 있으면 기존 계획을 200으로 돌려준다 |
| GET | 200 / 404 | 계획 조회 |

명세: `docs/api/curriculum-api.md` (저장소 루트 기준)

## 201과 200을 구분하는 이유

이미 계획이 있으면 새로 만들지 않는다. 만들지 않았는데 201을 돌려주면
클라이언트가 생성 여부를 알 수 없다. 서버가 실제로 한 일을 상태 코드로 알려준다.

## Request Body가 없다

남은 학습 시간과 Topic은 이미 DB에 있다. 프론트가 다시 보낼 이유가 없고,
보내면 서버가 가진 값과 어긋날 수 있다.

---

## StudyStepController

| 메서드 | 경로 | 상태 |
| --- | --- | --- |
| POST | `/{stepId}/start` | 200 |
| POST | `/{stepId}/complete` | 200 |

명세: `docs/api/study-step-api.md` (저장소 루트 기준)

여기도 Request Body가 없다. 실제 학습시간은 서버가 `startedAt` 과 현재 시각으로 계산한다.
화면 타이머 값을 받아 저장하면 조작할 수 있고, 여러 기기에서 접속했을 때
어느 값을 믿을지도 정할 수 없다.

시작에 201을 쓰지 않는 이유는 단계를 새로 만드는 것이 아니라
이미 있는 단계의 상태를 바꾸기 때문이다.

상태 전이 규칙은 서비스와 엔티티에 있다. 컨트롤러는 매핑과 응답 변환만 한다.
