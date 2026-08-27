# session/controller 작업 절차

## 엔드포인트를 추가할 때

```
1. 서비스 메서드가 이미 있는지 확인한다
2. 매핑 애노테이션 + 파라미터 바인딩만 작성한다
3. 응답은 DTO 정적 팩터리로 변환한다
4. 상태 코드를 명시한다 (생성은 201, 조회는 200)
5. @WebMvcTest 로 슬라이스 테스트를 작성한다
6. 통합 테스트에 실제 왕복을 추가한다
7. docs/api/session-api.md 갱신
```

## 테스트 방식

```java
@WebMvcTest(SessionController.class)
@MockitoBean SessionService sessionService;
```

@WebMvcTest 는 @RestControllerAdvice 를 함께 로드하므로 에러 응답까지 검증할 수 있다.
서비스는 목으로 대체하므로 DB가 필요 없다.

실제 검증 항목:

```
POST /api/sessions             201
GET  /api/sessions/{정상코드}    200
GET  /api/sessions/{없는코드}    404 SESSION_NOT_FOUND
GET  /api/sessions/ABC         400 INVALID_SESSION_CODE
```

## 검증

```bash
./gradlew test --tests "*SessionControllerTest*"
```
