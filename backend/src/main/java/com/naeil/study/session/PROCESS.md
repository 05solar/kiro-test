# session 작업 절차

## 세션에 필드를 추가할 때

```
1. StudySession 엔티티에 필드 + Javadoc (왜 필요한지)
2. 값을 바꾸는 도메인 메서드 추가 (세터를 만들지 않는다)
3. SessionResponse DTO에 필드 추가
4. 테스트 수정
   - SessionServiceTest      생성 직후 기본값
   - SessionControllerTest   응답 JSON 키 존재
   - StudySessionRepositoryTest  영속화 확인
   - SessionApiIntegrationTest   실제 응답 확인
5. docs/api/session-api.md 필드 표 + 예시 JSON
6. docs/database.md 컬럼 표
7. ./gradlew test
```

DTO만 고치고 테스트를 빠뜨리면 응답이 조용히 바뀐다. 위 4단계를 건너뛰지 않는다.

## 새 세션 API를 추가할 때

```
1. 도메인 메서드부터 만든다 (엔티티가 스스로 상태를 바꾸게)
2. 서비스에 유스케이스 메서드 추가 (@Transactional)
3. 요청 DTO + Bean Validation
4. 컨트롤러 매핑
5. 실패 케이스 예외 정의
6. 테스트: 서비스 단위 → 컨트롤러 슬라이스 → 통합
7. 문서 갱신
```

## STEP 3 (시험 정보 입력) — 구현 완료

```
PUT /api/sessions/{sessionCode}/exam

StudySession.updateExamInfo(subject, examAt, available, remaining, now)
    - availableStudyMinutes  사용자 입력 원본값
    - remainingStudyMinutes  min(입력값, 시험까지 남은 실제 분)
    - updatedAt 갱신
    - status 는 바꾸지 않는다

SessionService.calculateEffectiveStudyMinutes(requested, now, examAt)
    - ChronoUnit.MINUTES 기준, 초 단위는 버린다

검증
    - examAt 이 현재보다 미래인가         → INVALID_EXAM_TIME (서비스, Clock 사용)
    - subject 공백 불가 / 100자 이하       → Bean Validation
    - availableStudyMinutes 1~10080       → Bean Validation
```

시험 정보 등록도 세션 접근으로 보고 `touch()` 를 함께 호출한다.

## STEP 4 (파일 업로드) 예정 작업

```
1. Document 엔티티 + StudySession 과의 연관관계 (FK 는 UUID id)
2. 업로드 시 status: CREATED → UPLOADING
3. 업로드 진입 조건으로 hasExamInfo() 확인
4. 확장자/개수/용량 검증
```

## 검증

```bash
./gradlew test --tests "*Session*"
./gradlew build
```
