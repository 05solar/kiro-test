# session/entity

세션 도메인의 상태와 규칙.

| 클래스 | 역할 |
| --- | --- |
| `StudySession` | 학습 세션 엔티티 |
| `SessionStatus` | 세션 생명주기 Enum |
| `SessionCodePolicy` | 8자리 코드의 형식 규칙 (상수 + 검증) |

## StudySession

```
id                     UUID, 내부 식별자
sessionCode            8자리 접근 키, UNIQUE
subject                과목명 (STEP 3)
examAt                 시험 일시 (STEP 3)
availableStudyMinutes  최초 전체 학습 가능 시간, 진행 중 불변
remainingStudyMinutes  현재 남은 학습 가능 시간, STEP 완료마다 감소
status                 SessionStatus
currentStepOrder       현재 STEP 순번
createdAt / updatedAt / lastAccessedAt / expiresAt
```

### 제공하는 동작

| 메서드 | 설명 |
| --- | --- |
| `create(code, now, ttlDays)` | 생성. 상태는 `CREATED`, 만료는 `now + ttlDays` |
| `touch(now, ttlDays)` | 접근 기록. `lastAccessedAt`, `expiresAt`, `updatedAt` 갱신 |
| `isExpired(now)` | 보관 기한 초과 여부 |

세터는 없다. 시각은 항상 파라미터로 받는다.

## SessionCodePolicy

```
ALPHABET  ABCDEFGHJKLMNPQRSTUVWXYZ23456789   (32자)
LENGTH    8
PATTERN   ^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$
```

코드 생성기와 입력 검증이 같은 규칙을 공유하도록 여기 한 곳에만 정의한다.

> 기획 문서의 제외 목록에는 `L` 이 있지만 허용 문자열에는 `L` 이 포함되어 있다.
> 문자열을 정본으로 채택했다. `1` 과 `I` 가 이미 빠져 있어 `L` 은 혼동 대상이 없다.
> 정책을 바꾸려면 `ALPHABET` 과 `PATTERN` 두 상수만 고치면 된다.

## SessionStatus

```
CREATED → UPLOADING → ANALYZING → READY → IN_PROGRESS → COMPLETED
                                                       ↘ EXPIRED
```

현재 단계에서 실제로 쓰는 값은 `CREATED` 뿐이다. 나머지는 이후 단계를 위해 미리 정의했다.
