# session/service

세션 유스케이스.

| 클래스 | 역할 |
| --- | --- |
| `SessionService` | 세션 생성 / 조회 / 시험 정보 등록 |
| `SessionCodeGenerator` | 8자리 코드 생성 (SecureRandom) |

## SessionService

| 메서드 | 설명 |
| --- | --- |
| `createSession()` | 중복되지 않는 코드를 발급하고 세션을 저장한다 |
| `getSessionAndTouch(code)` | 형식 검증 → 조회 → 접근시각/보관기한 갱신 |
| `updateExamInfo(code, request)` | 시험 정보 저장 + 실제 사용 가능 시간 계산 |

### 이름에 touch 가 들어간 이유

조회처럼 보이지만 상태를 바꾼다. 만료 기준이 마지막 접근 후 30일이라 조회 자체가 쓰기 작업이다.
이름으로 그 사실을 드러내 readOnly 트랜잭션에서 호출하는 실수를 막는다.

### 코드 중복 처리

```
generate()
   ↓
existsBySessionCode()
   ↓ 중복이면
재생성 (최대 10회)
   ↓ 모두 중복이면
SessionCodeGenerationException (500)
```

32^8 = 약 1조 개의 코드 공간이므로 정상 상황에서 재시도는 거의 발생하지 않는다.

## SessionCodeGenerator

SecureRandom 으로 `SessionCodePolicy.ALPHABET` 에서 8글자를 뽑는다.
DB를 모른다. 중복 검사는 서비스의 책임이다.

## 의존성

```
SessionService
├── StudySessionRepository
├── SessionCodeGenerator
├── Clock                                   시간 고정 테스트를 위해 주입
├── session.expiration-days                 기본 30
└── session.code.max-generation-attempts    기본 10
```
