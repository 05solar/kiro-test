# analysis/exception 작업 절차

## 새 예외를 추가하는 순서

```
1. common/exception/ErrorCode 에 항목 추가 (HTTP 상태 + 한국어 메시지)
2. 여기에 BusinessException 하위 클래스 생성
3. 서비스나 검증기에서 throw
4. 테스트: 서비스 단위 + 컨트롤러(상태 코드, code 문자열)
5. docs/api/error-codes.md 와 docs/api/analysis-api.md 갱신
```

## 실패 원인을 세분화할 때

나누기 전에 확인한다.

```
사용자가 다르게 행동할 수 있는 구분인가?
(AI 타임아웃 / 응답 형식 오류 / 결과 없음 → 사용자는 모두 "다시 시도" 뿐이다)
```

아니라면 로그로 구분하고 응답은 하나로 유지한다.
`AiAnalysisException.reason` 이 그 역할을 한다.

## 검증

```bash
./gradlew test --tests "*AnalysisControllerTest*"
```
