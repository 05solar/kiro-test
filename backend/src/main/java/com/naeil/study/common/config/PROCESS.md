# common/config 작업 절차

## 새 설정 클래스를 추가할 때

```
1. 정말 전역 설정인지 확인한다 (도메인 설정은 해당 도메인 패키지에)
2. @Configuration 클래스 하나에 하나의 관심사만 담는다
3. 프로퍼티가 필요하면 application.yml 에 환경변수 형태로 추가한다
4. backend/README.md 의 환경변수 표를 갱신한다
```

## 추가 예정

| 시점 | 설정 |
| --- | --- |
| 프론트엔드 연동 시 | CORS 설정 (`WebMvcConfigurer`) |
| STEP 4 | 파일 업로드 크기 제한 |
| STEP 5 | LLM API 클라이언트, 비동기 실행자(`@EnableAsync`) |
