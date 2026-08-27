# analysis/service — 에이전트 작업 규칙

## 지킬 것

- `AnalysisService` 에 `@Transactional` 을 붙이지 않는다.
- 상태 변경은 `AnalysisStateWriter` 를 통한다.
- 엔티티를 트랜잭션 밖으로 들고 나가지 않는다. `AnalysisTarget` 으로 복사한다.
- 어느 실패든 `ANALYSIS_FAILED` 로 끝나게 한다.
- 실패해도 세션, 자료, 학습 맥락을 지우지 않는다.
- 중간 결과(주제 후보)를 DB에 저장하지 않는다.
- 로그에 `sessionId`, 개수, 처리 시간만 남긴다.

## 남은 시간

`remainingStudyMinutes` 로 Topic을 걸러 내지 않는다.
전체 Topic을 만들고, 시간에 맞추는 일은 커리큘럼 단계에서 한다.

## 하지 말 것

- 조각 하나가 실패했다고 조용히 건너뛰기
- 응답 검증 실패를 여러 번 재시도
- 컨트롤러에서 AI 호출
- 추출 텍스트나 AI 응답 전문을 로그로 출력
