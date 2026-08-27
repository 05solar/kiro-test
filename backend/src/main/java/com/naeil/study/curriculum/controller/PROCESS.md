# curriculum/controller 작업 절차

## 엔드포인트를 추가할 때

```
1. 정말 필요한지 먼저 판단한다
2. 매핑과 DTO 변환만 작성한다
3. @WebMvcTest 로 슬라이스 테스트 작성
4. docs/api/curriculum-api.md 갱신
```

## 검증 항목

```
POST 새로 생성        201
POST 이미 있음        200
POST 분석 미완료      400 SESSION_NOT_READY
POST Topic 없음       400 TOPICS_REQUIRED
POST 남은 시간 없음    400 NO_STUDY_TIME_AVAILABLE
POST 계획 생성 불가    422 CURRICULUM_GENERATION_FAILED
POST 없는 세션        404
GET  존재            200
GET  없음            404 CURRICULUM_NOT_FOUND
```

## STEP 8 에서 한 일

예정대로 `StudyStepController` 를 따로 만들었다.

```
POST /api/sessions/{code}/steps/{stepId}/start      200
POST /api/sessions/{code}/steps/{stepId}/complete   200
```

둘 다 200이다. 단계를 새로 만드는 것이 아니라 있는 단계의 상태를 바꾸기 때문이다.

## 검증

```bash
./gradlew test --tests "*CurriculumControllerTest*" --tests "*StudyStepControllerTest*"
```
