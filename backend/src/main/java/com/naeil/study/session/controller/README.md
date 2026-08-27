# session/controller

세션 REST API.

| 클래스 | 역할 |
| --- | --- |
| `SessionController` | `/api/sessions` 매핑 |

## 엔드포인트

| 메서드 | 경로 | 상태 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/sessions` | 201 | 세션 생성, 8자리 코드 발급 |
| GET | `/api/sessions/{sessionCode}` | 200 | 세션 조회 + 접근시각/보관기한 갱신 |
| PUT | `/api/sessions/{sessionCode}/exam` | 200 | 시험 정보 등록/수정 |

명세: `docs/api/session-api.md` (저장소 루트 기준)

## 이 클래스가 하는 일

```
HTTP 요청 → 서비스 호출 → DTO 변환 → ResponseEntity
```

그 이상은 하지 않는다. 코드 생성, 형식 검증, 트랜잭션은 모두 서비스 아래에 있다.

## 회원 개념이 없다는 뜻

인증 필터도, 토큰 파싱도, @AuthenticationPrincipal 도 없다.
경로의 8자리 코드가 곧 접근 권한이다. 따라서 이 컨트롤러가 학습 공간으로 들어가는
유일한 문이며, 여기서 새는 정보는 그대로 공격 표면이 된다.
