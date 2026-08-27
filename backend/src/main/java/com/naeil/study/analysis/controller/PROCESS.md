# analysis/controller 작업 절차

## 비동기로 옮길 때

```
1. 서비스 호출을 별도 실행자로 옮긴다
2. 응답을 202 + ANALYZING 으로 바꾼다
3. AnalysisResponse 를 고친다
4. docs/api/analysis-api.md 의 응답 예시와 상태 설명을 고친다
```

컨트롤러에서 AI를 직접 부르지 않으므로 서비스는 그대로 쓸 수 있다.

## 검증 항목

```
POST 정상               200 + READY + topicCount
POST 시험 정보 없음      400 EXAM_INFO_REQUIRED
POST PARSED 자료 없음    400 NO_PARSED_DOCUMENT
POST 분석 중            409 ANALYSIS_ALREADY_RUNNING
POST 분석 실패          502 ANALYSIS_FAILED
POST 없는 세션          404
```

## 검증

```bash
./gradlew test --tests "*AnalysisControllerTest*"
```
