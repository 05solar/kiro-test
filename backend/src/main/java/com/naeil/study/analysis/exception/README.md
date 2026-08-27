# analysis/exception

| 예외 | HTTP | code |
| --- | --- | --- |
| `ExamInfoRequiredException` | 400 | `EXAM_INFO_REQUIRED` |
| `NoParsedDocumentException` | 400 | `NO_PARSED_DOCUMENT` |
| `AnalysisAlreadyRunningException` | 409 | `ANALYSIS_ALREADY_RUNNING` |
| `AiAnalysisException` | 502 | `ANALYSIS_FAILED` |

## AiAnalysisException 이 502인 이유

사용자의 요청은 정상이다. 실패는 외부 AI 호출이나 그 응답에서 생긴다.
500(서버 내부 오류)보다 502(게이트웨이 오류)가 원인을 더 정확히 가리킨다.

`reason` 필드는 로그와 진단용 내부 요약이다. 사용자 응답에는 나가지 않는다.
`ai call failed: topic merge`, `ai returned no topics` 같은 값이 들어간다.

## 400 두 종류

둘 다 사용자가 앞 단계를 마치면 해결된다.

```
EXAM_INFO_REQUIRED   시험 정보를 입력하면 된다
NO_PARSED_DOCUMENT   자료를 올리고 파싱하면 된다
```

메시지에 무엇을 하면 되는지 적는다.

## 409를 쓰는 이유

이미 분석 중인 세션에 다시 요청하면 같은 자료를 두 번 분석해 결과를 덮어쓰고,
AI 호출 비용이 두 배로 든다. 문서 파싱의 `DOCUMENT_ALREADY_PARSING` 과 같은 정책이다.
