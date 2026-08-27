# session

학습 세션 도메인. 이 서비스의 최상위 도메인이며, 회원 개념이 없으므로 모든 접근이 여기서 시작한다.

```
session
├── controller/   REST API
├── service/      유스케이스 + 세션 코드 생성기
├── repository/   Spring Data JPA
├── entity/       StudySession, SessionStatus, SessionCodePolicy
├── dto/          요청/응답 record
└── exception/    도메인 예외
```

## 핵심 개념

### 세션 코드는 접근 키다

```
7K2M9QXF
```

8자리 코드를 아는 사람은 그 학습 공간에 접근할 수 있다. 비밀번호도 토큰도 없다.
따라서 코드는 서버가 `SecureRandom` 으로만 발급하고, 사용자는 지정할 수 없다.

### 식별자가 둘이다

```
id           UUID       내부 관계용. 외부 노출 금지
sessionCode  VARCHAR(8) 사용자 노출용 접근 키
```

### 만료는 마지막 접근 기준이다

조회에 성공할 때마다 보관 기한이 연장된다.

```
lastAccessedAt = now
expiresAt      = now + 30일
```

### 학습 시간 값이 둘이다

```
availableStudyMinutes  최초 입력한 전체 학습 가능 시간. 진행 중 불변
remainingStudyMinutes  현재 남아 있는 학습 가능 시간. STEP 완료마다 감소
```

동적 커리큘럼 재조정은 `remainingStudyMinutes` 를 기준으로 한다.
현재 단계에서는 두 필드 모두 컬럼만 준비되어 있고 값을 채우는 로직은 없다.

## 구현된 API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/sessions` | 세션 생성, 8자리 코드 발급 |
| GET | `/api/sessions/{sessionCode}` | 세션 조회 + 접근시각/보관기한 갱신 |
| PUT | `/api/sessions/{sessionCode}/exam` | 시험 정보 등록/수정 |

명세: `docs/api/session-api.md` (저장소 루트 기준)

## 아직 없는 것

```
Document 연관관계            STEP 4
Topic / StudyStep 연관관계   STEP 5~6
진행률 계산                  STEP 7
remainingStudyMinutes 차감   STEP 7
만료 세션 자동 삭제           P1
요청 수 제한 (레이트리밋)     P0 보안 TODO
코드 HMAC 해시 저장           P0 보안 TODO
```
