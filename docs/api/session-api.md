# 세션 API 명세

학습 세션의 생성과 복구를 담당하는 API. 이 서비스에는 회원 개념이 없으므로,
아래 두 엔드포인트가 사용자가 자신의 학습 공간에 들어가는 유일한 경로다.

- Base URL: `/api/sessions`
- Content-Type: `application/json`
- 인증: 없음. **8자리 세션 코드 자체가 접근 키**다.

## 목차

- [POST /api/sessions — 세션 생성](#post-apisessions--세션-생성)
- [GET /api/sessions/{sessionCode} — 세션 조회](#get-apisessionssessioncode--세션-조회)
- [PUT /api/sessions/{sessionCode}/exam — 시험 정보 등록/수정](#put-apisessionssessioncodeexam--시험-정보-등록수정)
- [세션 코드 규칙](#세션-코드-규칙)
- [필드 정의](#필드-정의)
- [상태 값](#상태-값)
- [보안 정책](#보안-정책)

---

## POST /api/sessions — 세션 생성

새 학습 세션을 만들고 8자리 접근 코드를 발급한다.

### 요청

```http
POST /api/sessions
```

Request Body 없음. 사용자가 코드를 지정할 수 없다.

### 응답 — 201 Created

```json
{
  "sessionCode": "7K2M9QXF",
  "status": "CREATED"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sessionCode` | string(8) | 서버가 발급한 접근 코드 |
| `status` | string | 항상 `CREATED` |

### 생성 직후 세션 상태

시험 정보를 아직 입력하지 않았으므로 다음 필드는 모두 `null`이다.

```
subject                = null
examAt                 = null
availableStudyMinutes  = null
remainingStudyMinutes  = null
currentStepOrder       = null
```

시각 필드는 다음과 같이 채워진다.

```
createdAt       = now
updatedAt       = now
lastAccessedAt  = now
expiresAt       = now + 30일
```

### 오류

| 상태 | code | 상황 |
| --- | --- | --- |
| 500 | `SESSION_CODE_GENERATION_FAILED` | 10회 시도에도 중복되지 않는 코드를 만들지 못한 경우 (정상 상황에서는 발생하지 않음) |

### 클라이언트 처리

발급받은 코드는 브라우저 LocalStorage에 저장해 재접속 편의를 제공한다.

```
study:lastSessionCode = "7K2M9QXF"
```

LocalStorage는 편의용 캐시일 뿐이다. 커리큘럼, 진행률, 퀴즈 결과 같은 실제 데이터는 저장하지 않는다.

---

## GET /api/sessions/{sessionCode} — 세션 조회

8자리 코드로 기존 학습 세션을 불러온다. 다른 기기에서 학습을 이어가는 경로다.

### 요청

```http
GET /api/sessions/7K2M9QXF
```

| 파라미터 | 위치 | 필수 | 설명 |
| --- | --- | --- | --- |
| `sessionCode` | path | 예 | 8자리 세션 코드 |

### 부수 효과

조회에 성공하면 보관 기한이 연장된다. 세션 만료는 **마지막 접근 후 30일** 기준이다.

```
lastAccessedAt = now
expiresAt      = now + 30일
updatedAt      = now
```

### 응답 — 200 OK

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

값이 없는 필드도 키는 그대로 내려간다. 내부 식별자(`id`, UUID)는 **응답에 포함하지 않는다.**

### 오류

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_SESSION_CODE` | 코드 형식이 규칙에 맞지 않음. DB를 조회하지 않고 즉시 반환 |
| 404 | `SESSION_NOT_FOUND` | 해당 코드의 세션이 없음 |

```json
{
  "code": "SESSION_NOT_FOUND",
  "message": "유효한 학습 세션을 찾을 수 없습니다."
}
```

### 요청 예시별 응답

| 요청 | 결과 |
| --- | --- |
| `GET /api/sessions/7K2M9QXF` (존재) | 200 |
| `GET /api/sessions/ZZZZZZZZ` (형식 정상, 없음) | 404 `SESSION_NOT_FOUND` |
| `GET /api/sessions/ABC` (길이 부족) | 400 `INVALID_SESSION_CODE` |
| `GET /api/sessions/abc12345` (소문자) | 400 `INVALID_SESSION_CODE` |
| `GET /api/sessions/ABCDEFG0` (제외 문자 `0`) | 400 `INVALID_SESSION_CODE` |
| `GET /api/sessions/12345678!` (허용하지 않는 기호) | 400 `INVALID_SESSION_CODE` |

---

## PUT /api/sessions/{sessionCode}/exam — 시험 정보 등록/수정

과목명, 시험 일시, 공부 가능한 시간을 저장한다. 같은 API를 다시 호출하면 덮어쓴다.

### 요청

```http
PUT /api/sessions/7K2M9QXF/exam
Content-Type: application/json
```

```json
{
  "subject": "운영체제",
  "examAt": "2026-08-28T10:00:00",
  "availableStudyMinutes": 360
}
```

| 필드 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `subject` | string | 예 | 공백만 입력 불가, 최대 100자 |
| `examAt` | datetime | 예 | 현재 시각보다 미래 |
| `availableStudyMinutes` | int | 예 | 1 이상 10080 이하 (최대 7일) |

과목명의 앞뒤 공백은 제거하고 저장한다.

### 서버가 하는 계산

사용자가 입력한 학습 시간이 시험까지 남은 실제 시간보다 클 수 있다.
이때 입력값을 그대로 쓰면 실행 불가능한 계획이 만들어지므로, 서버가 실제 사용 가능한 시간을 다시 정한다.

```
minutesUntilExam      = examAt - 현재 시각   (ChronoUnit.MINUTES, 초 단위 버림)
effectiveStudyMinutes = min(availableStudyMinutes, minutesUntilExam)

availableStudyMinutes = 사용자 입력 원본값     (그대로 저장)
remainingStudyMinutes = effectiveStudyMinutes  (계산 결과 저장)
```

예:

```
현재 18:00, 시험 22:00, 사용자 입력 360분
→ 시험까지 240분
→ availableStudyMinutes = 360
   remainingStudyMinutes = 240
```

`availableStudyMinutes`를 원본으로 남겨 두는 이유는, 이후 "최초 계획 대비 실제 학습" 비교와
동적 커리큘럼 재조정에서 기준값이 필요하기 때문이다.

### 부수 효과

시험 정보 등록도 세션 접근으로 간주한다.

```
lastAccessedAt = now
expiresAt      = now + 30일
updatedAt      = now
```

세션 상태는 바뀌지 않는다. `CREATED`를 그대로 유지하며, `UPLOADING`으로의 전환은
파일 업로드 단계에서 일어난다.

### 응답 — 200 OK

```json
{
  "sessionCode": "7K2M9QXF",
  "subject": "운영체제",
  "examAt": "2026-08-28T10:00:00",
  "availableStudyMinutes": 360,
  "remainingStudyMinutes": 360,
  "status": "CREATED"
}
```

시험까지 240분밖에 없는 경우:

```json
{
  "sessionCode": "7K2M9QXF",
  "subject": "운영체제",
  "examAt": "2026-08-27T22:00:00",
  "availableStudyMinutes": 360,
  "remainingStudyMinutes": 240,
  "status": "CREATED"
}
```

### 오류

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_SESSION_CODE` | 세션 코드 형식 오류 |
| 400 | `INVALID_EXAM_TIME` | 시험 일시가 현재 시각과 같거나 과거 |
| 400 | `INVALID_REQUEST` | 요청 값 검증 실패. `message`에 실패한 항목의 안내가 담긴다 |
| 404 | `SESSION_NOT_FOUND` | 해당 코드의 세션이 없음 |

```json
{
  "code": "INVALID_EXAM_TIME",
  "message": "시험 시간은 현재 시간보다 이후여야 합니다."
}
```

```json
{
  "code": "INVALID_REQUEST",
  "message": "공부 가능한 시간은 1분 이상이어야 합니다."
}
```

### 조회

시험 정보 전용 조회 API는 만들지 않는다. `GET /api/sessions/{sessionCode}` 응답에 이미 포함되어 있다.

---

## 세션 코드 규칙

```
길이      정확히 8자리
문자 집합  ABCDEFGHJKLMNPQRSTUVWXYZ23456789   (32자)
정규식    ^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$
```

혼동하기 쉬운 `0`, `O`, `1`, `I`는 제외한다.

> **명세 모순 참고**
> 기획 문서의 제외 목록에는 `L`도 있지만, 같은 문서가 제시한 허용 문자열에는 `L`이 포함되어 있다.
> 두 문서에서 문자열이 동일하고 32자로 떨어지므로 문자열을 정본으로 채택했다.
> `1`과 `I`가 이미 제외되어 `L`은 혼동 대상이 없다.
> 정책을 바꾸려면 `SessionCodePolicy.ALPHABET`과 `PATTERN` 두 상수만 수정하면 된다.

생성 방식:

```
SecureRandom으로 8자리 생성
        ↓
DB 중복 확인 (existsBySessionCode)
        ↓
중복이면 재생성 (최대 10회)
        ↓
저장 (session_code에 UNIQUE 제약)
```

---

## 필드 정의

| 필드 | 타입 | null 허용 | 설명 |
| --- | --- | --- | --- |
| `sessionCode` | string(8) | 아니오 | 접근 키. 서버 발급, 변경 불가 |
| `subject` | string | 예 | 과목명. STEP 3에서 입력 |
| `examAt` | datetime | 예 | 시험 일시. STEP 3에서 입력 |
| `availableStudyMinutes` | int | 예 | 최초 입력한 **전체** 학습 가능 시간(분) |
| `remainingStudyMinutes` | int | 예 | 현재 **남아 있는** 학습 가능 시간(분) |
| `status` | enum | 아니오 | 세션 상태 |
| `currentStepOrder` | int | 예 | 현재 진행 중인 STEP 순번 |
| `createdAt` | datetime | 아니오 | 생성 시각 |
| `lastAccessedAt` | datetime | 아니오 | 마지막 접근 시각 |
| `expiresAt` | datetime | 아니오 | 보관 만료 시각 (`lastAccessedAt + 30일`) |

### availableStudyMinutes 와 remainingStudyMinutes

두 값의 의미가 다르다. 동적 커리큘럼 재조정의 기준이 되는 값이므로 혼동하면 안 된다.

```
availableStudyMinutes  최초 입력한 전체 학습 가능 시간. 진행 중 변경하지 않는다.
remainingStudyMinutes  현재 남아 있는 학습 가능 시간. STEP 완료마다 감소한다.
```

시험 정보 입력 시점(STEP 3)에는 두 값이 같다.

```
availableStudyMinutes = 360
remainingStudyMinutes = 360
```

이후 STEP 완료 시(STEP 7 구현 예정):

```
STEP 1 실제 학습시간 = 42분

remainingStudyMinutes   360 → 318
availableStudyMinutes   360 (그대로)
```

남은 STEP의 시간 재배분은 `remainingStudyMinutes`를 기준으로 한다.

> 현재 단계에서는 두 필드 모두 **컬럼만 존재**한다. 값을 채우거나 차감하는 로직은 없다.

---

## 상태 값

```
CREATED      세션 생성됨 (현재 단계에서 만들어지는 유일한 상태)
UPLOADING    강의자료 업로드 중
ANALYZING    AI 분석 중
READY        커리큘럼 생성 완료
IN_PROGRESS  학습 진행 중
COMPLETED    전체 학습 완료
EXPIRED      보관 기한 만료
```

```
CREATED → UPLOADING → ANALYZING → READY → IN_PROGRESS → COMPLETED
                                                       ↘ EXPIRED
```

---

## 보안 정책

세션 코드 하나로 학습 공간 전체에 접근할 수 있으므로 다음을 지킨다.

| 정책 | 현재 상태 |
| --- | --- |
| 코드는 서버에서 `SecureRandom`으로만 발급 | 적용됨 |
| 잘못된 코드에 동일한 메시지 반환 (존재 여부 노출 금지) | 적용됨 |
| 내부 UUID를 응답에 포함하지 않음 | 적용됨 |
| 에러 응답에 내부 정보(생성 시각, 스택트레이스) 미포함 | 적용됨 |
| 코드 추측 방지를 위한 요청 수 제한 (IP 기준 1분 10회) | **미구현 (TODO)** |
| 코드 원문 대신 HMAC-SHA256 해시 저장 | **미구현 (TODO)** |
| 만료 세션 자동 삭제 스케줄러 | **미구현 (TODO)** |

미구현 항목은 [README.md](README.md)의 TODO 목록에서 관리한다.
