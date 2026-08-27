# backend — 에이전트 작업 규칙

저장소 전체 규칙은 [../AGENT.md](../AGENT.md)를 먼저 읽는다. 여기에는 백엔드 코드에만 해당하는 규칙을 적는다.

## 코드 컨벤션

- 들여쓰기 4칸, `import`는 와일드카드 금지 (정적 임포트 제외).
- **생성자 주입**만 쓴다. `@Autowired` 필드 주입, `@RequiredArgsConstructor` 모두 쓰지 않는다.
  의존성이 늘어나는 것을 생성자에서 바로 보이게 하려는 의도다.
- Lombok은 엔티티의 `@Getter`, `@NoArgsConstructor(access = PROTECTED)` 정도로 제한한다.
  서비스/컨트롤러에는 쓰지 않는다.
- DTO는 `record`로 만들고, 엔티티 → DTO 변환은 DTO의 정적 팩터리(`from`)에 둔다.
- 주석은 "무엇을"이 아니라 "왜"를 적는다. 코드를 읽으면 알 수 있는 내용은 적지 않는다.

## 엔티티 규칙

- 세터를 만들지 않는다. 상태 변경은 의도가 드러나는 도메인 메서드로 한다 (`touch`, `registerExamInfo`).
- 생성은 정적 팩터리(`create`)로만 한다. `public` 생성자를 열지 않는다.
- 시각은 파라미터로 받는다. 엔티티 안에서 `LocalDateTime.now()`를 호출하지 않는다.
  `@PrePersist` / `@CreatedDate` 도 쓰지 않는다. 테스트에서 시간을 고정할 수 없게 되기 때문이다.
- 컬럼명은 `snake_case`로 명시한다. 암묵적 네이밍 전략에 기대지 않는다.

## 서비스 규칙

- 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`을 붙인다.
- 조회 후 상태를 바꾸는 메서드(`getSessionAndTouch`)는 이름에 그 사실이 드러나게 한다.
  읽기처럼 보이는 이름으로 쓰기를 하지 않는다.

## 예외 규칙

- 새 예외는 `BusinessException`을 상속하고 `ErrorCode`를 하나 갖는다.
- HTTP 상태 코드는 `ErrorCode`에 있다. 컨트롤러나 서비스에서 상태 코드를 다루지 않는다.
- 예상하지 못한 예외의 메시지는 응답에 넣지 않는다. 로그로만 남긴다.

## 테스트 규칙

- `@DisplayName`을 한글로 붙인다. 테스트 목록이 그대로 기능 명세가 되게 한다.
- 무작위성이 있는 코드(`SecureRandom`)는 `@RepeatedTest`나 충분한 반복 횟수로 검증한다.
- 통합 테스트에서 시간은 `MutableClock`으로 제어한다. `Thread.sleep`을 쓰지 않는다.
- 테스트가 외부 DB나 네트워크를 요구하게 만들지 않는다. H2로 끝나야 한다.

## 빌드 파일을 건드릴 때

- `gradle.properties`의 `-Dfile.encoding=MS949`는 한글 경로 대응이다. 지우면 테스트가 전부 깨진다.
- `build.gradle`의 `options.encoding = 'UTF-8'`과 테스트 `file.encoding=UTF-8`은
  소스와 런타임 인코딩을 UTF-8로 고정한다. 위 설정과 역할이 다르니 함께 지우지 않는다.
- 의존성을 추가할 때는 Spring Boot BOM이 관리하는 것은 버전을 적지 않는다.

## 하지 말 것

- `spring-boot-starter-security` 추가 (회원 개념이 없다)
- `@MockBean` 사용 (Boot 3.4부터 deprecated. `@MockitoBean`을 쓴다)
- 엔티티를 컨트롤러 응답으로 반환
- 내부 UUID를 응답에 포함
- 다음 STEP의 기능을 미리 구현
