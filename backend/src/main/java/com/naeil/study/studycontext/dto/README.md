# studycontext/dto

| 클래스 | 방향 | 쓰이는 곳 |
| --- | --- | --- |
| `UpdateStudyContextRequest` | 요청 | `PUT .../study-context` |
| `StudyContextResponse` | 응답 | `PUT` / `GET .../study-context` |

## UpdateStudyContextRequest

네 항목 모두 선택 입력이고 각각 2000자 이하다.

```java
@Size(max = StudyContextPolicy.MAX_FIELD_LENGTH, message = "...")
```

`@Size` 는 정규화 **전** 원본 길이를 본다. 앞뒤 공백도 길이에 포함된다.

## StudyContextResponse

```json
{
  "sessionCode": "7K2M9QXF",
  "professorEmphasis": "교착상태 조건 강조",
  "pastExamInfo": null,
  "weakAreas": "가상 메모리",
  "mustStudyAreas": null,
  "updatedAt": "2026-08-27T17:30:00"
}
```

| 팩터리 | 용도 |
| --- | --- |
| `from(sessionCode, studyContext)` | 저장된 학습 맥락 |
| `empty(sessionCode)` | 아직 입력하지 않은 세션 |

`empty()` 가 있는 이유는 조회에서 404를 쓰지 않기 때문이다.
프론트가 예외 처리 없이 입력 화면을 초기화할 수 있어야 한다.
