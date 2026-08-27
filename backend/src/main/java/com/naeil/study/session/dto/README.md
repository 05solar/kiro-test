# session/dto

세션 API의 요청/응답 형식. 모두 record 다.

| 클래스 | 방향 | 쓰이는 곳 |
| --- | --- | --- |
| `CreateSessionResponse` | 응답 | `POST /api/sessions` |
| `UpdateExamRequest` | 요청 | `PUT /api/sessions/{sessionCode}/exam` |
| `ExamResponse` | 응답 | `PUT /api/sessions/{sessionCode}/exam` |
| `SessionResponse` | 응답 | `GET /api/sessions/{sessionCode}` |

## CreateSessionResponse

```json
{ "sessionCode": "7K2M9QXF", "status": "CREATED" }
```

생성 직후에는 사용자가 기억해야 할 코드와 상태만 돌려준다.

## SessionResponse

```json
{
  "sessionCode": "7K2M9QXF",
  "subject": null,
  "examAt": null,
  "availableStudyMinutes": null,
  "remainingStudyMinutes": null,
  "status": "CREATED",
  "currentStepOrder": null,
  "createdAt": "2026-08-27T15:30:00",
  "lastAccessedAt": "2026-08-27T15:35:00",
  "expiresAt": "2026-09-26T15:35:00"
}
```

내부 식별자(id, UUID)는 포함하지 않는다. 사용자에게 노출되는 식별자는 세션 코드뿐이다.

값이 없는 필드도 키는 그대로 내려간다. 프론트엔드가 필드 존재 여부로 분기하지 않게 하기 위해서다.

## 변환

엔티티에서 DTO로의 변환은 DTO의 정적 팩터리에 둔다.

```java
SessionResponse.from(session)
```

서비스나 컨트롤러에 변환 코드를 흩어 놓지 않는다.
