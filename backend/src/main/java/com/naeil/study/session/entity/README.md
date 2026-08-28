# session/entity

세션 도메인의 상태와 규칙.

| 클래스 | 역할 |
| --- | --- |
| `StudySession` | 학습 세션 엔티티 |
| `SessionStatus` | 세션 생명주기 Enum |
| `StudySourceType` | 학습 내용을 무엇에 근거해 만들었는지 |
| `SessionCodePolicy` | 8자리 코드의 형식 규칙 (상수 + 검증) |

## StudySession

```
id                     UUID, 내부 식별자
sessionCode            8자리 접근 키, UNIQUE
subject                과목명 (STEP 3)
examScope              시험 범위 (STEP 3, 선택). 자료가 없을 때의 유일한 근거
examAt                 시험 일시 (STEP 3)
availableStudyMinutes  최초 전체 학습 가능 시간, 진행 중 불변
remainingStudyMinutes  현재 남은 학습 가능 시간, STEP 완료마다 감소
status                 SessionStatus
sourceType             StudySourceType. 분석을 시작할 때 정해진다
currentStepOrder       현재 STEP 순번
createdAt / updatedAt / lastAccessedAt / expiresAt
```

### 제공하는 동작

| 메서드 | 설명 |
| --- | --- |
| `create(code, now, ttlDays)` | 생성. 상태는 `CREATED`, 만료는 `now + ttlDays` |
| `touch(now, ttlDays)` | 접근 기록. `lastAccessedAt`, `expiresAt`, `updatedAt` 갱신 |
| `isExpired(now)` | 보관 기한 초과 여부 |
| `startAnalyzing(sourceType, now)` | 분석 시작. 이때 근거가 무엇인지 확정한다 |
| `isGrounded()` | 실제 강의자료에 근거했는지 |
| `canGenerateFromGeneralKnowledge()` | 과목명과 시험 범위가 모두 있는지 |

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

## StudySourceType

학습 내용을 무엇에 근거해 만들었는지. 분석을 시작하는 시점에 정해지고, 이후 그 세션의
커리큘럼과 퀴즈 전체가 같은 근거를 따른다.

```
USER_MATERIAL      추출된 강의자료가 있다. 자료에서 뽑는다
GENERAL_KNOWLEDGE  자료가 없다. 과목명과 시험 범위로 일반 지식에서 만든다
null               아직 분석하지 않았다
```

`isGrounded()` 는 `USER_MATERIAL` 일 때만 true 다. 화면은 이 값으로 "학습자료 기반 /
일반 지식 기반"을 표시한다 — 그렇게 만들었다는 사실을 사용자에게 감추지 않는다.

자료 없이 만들려면 **과목명과 시험 범위가 모두** 있어야 한다. 둘 중 하나라도 비어 있으면
근거가 아무것도 없으므로 분석을 시작하지 않는다(`canGenerateFromGeneralKnowledge()`).
