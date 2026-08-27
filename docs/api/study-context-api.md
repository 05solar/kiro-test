# 학습 맥락 API 명세

강의자료만으로는 알 수 없는 정보를 사용자에게서 직접 받는다.
AI 분석 전에 입력하며, 네 항목 모두 선택 입력이다.

- Base URL: `/api/sessions/{sessionCode}/study-context`
- 인증: 없음. 8자리 세션 코드가 접근 키다.
- 세션당 최대 하나만 존재한다.

## 네 가지 질문

| 필드 | 화면 질문 |
| --- | --- |
| `professorEmphasis` | 교수님이 시험에 나온다고 강조한 부분이 있나요? |
| `pastExamInfo` | 기출문제 또는 예상 문제가 있나요? |
| `weakAreas` | 내가 자신 없는 부분은 무엇인가요? |
| `mustStudyAreas` | 반드시 공부하고 싶은 범위가 있나요? |

---

## PUT /api/sessions/{sessionCode}/study-context — 저장 / 수정

없으면 만들고 있으면 고친다(Upsert). 새 행을 덧붙이지 않는다.

### 요청

```http
PUT /api/sessions/7K2M9QXF/study-context
Content-Type: application/json
```

```json
{
  "professorEmphasis": "교수님이 교착상태 4가지 조건을 강조했습니다.",
  "pastExamInfo": "CPU Scheduling 계산 문제가 이전 시험에 출제되었습니다.",
  "weakAreas": "가상 메모리와 페이지 교체 알고리즘",
  "mustStudyAreas": "교착상태와 CPU Scheduling"
}
```

| 필드 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `professorEmphasis` | string | 아니오 | 2000자 이하 |
| `pastExamInfo` | string | 아니오 | 2000자 이하 |
| `weakAreas` | string | 아니오 | 2000자 이하 |
| `mustStudyAreas` | string | 아니오 | 2000자 이하 |

네 항목이 모두 `null`인 요청도 정상 처리한다.

```json
{
  "professorEmphasis": null,
  "pastExamInfo": null,
  "weakAreas": null,
  "mustStudyAreas": null
}
```

### 전체 교체다

PUT이므로 부분 수정이 아니다. **보내지 않은 항목은 비워진다.**

```
기존:  professorEmphasis = "강조 내용"
       weakAreas         = "가상 메모리"

요청:  { "weakAreas": "교착상태" }        (나머지 생략 = null)

결과:  professorEmphasis = null
       weakAreas         = "교착상태"
```

별도의 DELETE API를 만들지 않은 이유가 이것이다. 네 항목을 모두 비운 채 PUT하면
삭제와 같은 효과가 난다.

### 입력값 정규화

```
"   교착상태를 강조함   "  →  "교착상태를 강조함"     앞뒤 공백 제거
"     "                   →  null                    공백만 있으면 null
```

빈 문자열과 `null`이 섞이면 "입력하지 않음"을 판단하는 조건이 여기저기서 달라진다.
저장 시점에 하나로 통일한다.

### 부수 효과

```
lastAccessedAt = now
expiresAt      = now + 30일
```

**세션 상태는 바꾸지 않는다.** 학습 맥락은 세션 상태 머신과 독립적인 선택 정보다.

### 응답 — 200 OK

```json
{
  "sessionCode": "7K2M9QXF",
  "professorEmphasis": "교수님이 교착상태 4가지 조건을 강조했습니다.",
  "pastExamInfo": "CPU Scheduling 계산 문제가 이전 시험에 출제되었습니다.",
  "weakAreas": "가상 메모리와 페이지 교체 알고리즘",
  "mustStudyAreas": "교착상태와 CPU Scheduling",
  "updatedAt": "2026-08-27T17:30:00"
}
```

### 오류

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_SESSION_CODE` | 세션 코드 형식 오류 |
| 400 | `INVALID_REQUEST` | 항목이 2000자를 넘음. `message`에 어느 항목인지 담긴다 |
| 404 | `SESSION_NOT_FOUND` | 세션 없음 |

```json
{
  "code": "INVALID_REQUEST",
  "message": "교수님 강조 내용은 2000자 이하로 입력해 주세요."
}
```

---

## GET /api/sessions/{sessionCode}/study-context — 조회

### 응답 — 200 OK

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

### 아직 입력하지 않았어도 200이다

학습 맥락은 선택 입력이라 "없음"이 정상 상태다. 404로 응답하면 프론트가
정상 상태를 예외로 다뤄야 한다.

```json
{
  "sessionCode": "7K2M9QXF",
  "professorEmphasis": null,
  "pastExamInfo": null,
  "weakAreas": null,
  "mustStudyAreas": null,
  "updatedAt": null
}
```

이 응답을 그대로 입력 화면 초기값으로 쓸 수 있다.

### 오류

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_SESSION_CODE` | 세션 코드 형식 오류 |
| 404 | `SESSION_NOT_FOUND` | 세션 없음 |

---

## 삭제 API는 없다

네 항목을 모두 `null`로 PUT하면 같은 효과가 난다. 엔드포인트를 하나 더 만들 이유가 없다.

---

## 이후 AI 분석에서의 취급

이번 단계에서는 저장과 조회만 한다. 아래는 다음 단계의 전제다.

| 필드 | 용도 |
| --- | --- |
| `professorEmphasis` | Topic 우선순위 상향 |
| `pastExamInfo` | Topic 우선순위 + Quiz 유형 비중 상향 |
| `weakAreas` | 학습시간 / Quiz / 복습 비중 상향 |
| `mustStudyAreas` | **가중치가 아니라 제약.** 시간이 부족해도 커리큘럼에서 빼지 않는다 |

### 숫자 가중치를 하드코딩하지 않는다

```
교수 강조 × 2.0
기출 × 1.8
취약 × 1.5
```

이런 계수를 임의로 정하지 않는다. 초기에는 이 정보를 LLM 프롬프트 컨텍스트로 전달하고
결과 품질을 본다. 필요성이 확인된 뒤에 규칙과 AI를 섞는 방식으로 발전시킨다.

### 사용자 입력은 검증된 사실이 아니다

`pastExamInfo`에 "교착상태가 100% 시험에 나온다"가 들어와도 시스템이 이를 사실로 확정하면
안 된다. 프롬프트에서 **시스템 지시문과 사용자 제공 맥락을 명확히 분리**한다.

자유 입력이라 "위 지시를 무시하고..." 같은 내용이 들어올 수 있다.
이번 단계에는 AI가 없어 필터링을 구현하지 않았지만, **학습 맥락을 시스템 프롬프트 문자열에
그대로 이어붙이는 구조는 만들지 않는다.**
