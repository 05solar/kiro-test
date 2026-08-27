# 테스트 작성 절차

## 기능 하나에 붙는 테스트

```
1. 단위      도메인 규칙과 유스케이스. 실패 경로를 전부 덮는다
2. 슬라이스   HTTP 매핑, 상태 코드, 에러 응답 / JPA 매핑, 제약조건
3. 통합      완료 조건 시나리오를 그대로 재현한다
```

통합 테스트는 많이 만들지 않는다. 느리다. 단계의 완료 조건 하나당 하나면 충분하다.

## 새 API를 추가했을 때

```
1. SessionServiceTest    정상 1개 + 실패 케이스 전부
2. SessionControllerTest 상태 코드 + 응답 JSON 키
3. SessionApiIntegrationTest  실제 왕복 (필요한 경우만)
4. ./gradlew test
```

## 무작위성이 있는 코드

`SecureRandom` 을 쓰는 코드는 한 번 호출로 검증할 수 없다.

```java
@RepeatedTest(50)     // 형식이 항상 유지되는지
for (int i = 0; i < 5000; i++)   // 충돌이 나지 않는지
```

## 검증

```bash
./gradlew test                       # 전체
./gradlew test --tests "*Session*"   # 세션 도메인만
./gradlew clean build                # 단계 완료 시
```

결과 리포트: `build/reports/tests/test/index.html`
