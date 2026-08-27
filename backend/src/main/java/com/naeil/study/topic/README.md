# topic

AI가 강의자료를 분석해 만든 학습 단위.

```
topic
├── controller/   조회 API
├── service/      조회 유스케이스
├── repository/   Spring Data JPA
├── entity/       Topic, TopicImportance
└── dto/          응답 record
```

## 만드는 쪽과 읽는 쪽이 다르다

Topic을 만드는 것은 `analysis` 도메인이다. 이 패키지는 저장 구조와 조회를 담당한다.
분석 과정은 `analysis/service/AnalysisStateWriter` 가 `TopicRepository` 를 직접 쓴다.

## 핵심 개념

### importance는 출제 확률이 아니다

**학습 우선순위**다. 핵심 개념성, 자료 내 반복도, 다른 개념과의 연결성,
전체 이해에 필요한 정도로 판단한다. 화면에도 "시험 출제 가능성"이 아니라
"학습 우선순위"로 표시한다.

### estimatedStudyMinutes는 최종 계획이 아니다

"이 주제를 제대로 학습하는 데 필요한 시간"이다. 사용자의 남은 시간에 맞춘 조정은
커리큘럼 단계에서 한다. 그래서 전체 합이 `remainingStudyMinutes` 를 넘어도 정상이다.

### mustStudyMatched는 제약이다

나머지 세 boolean은 우선순위 힌트지만 이 값은 다르다.
커리큘럼 단계에서 시간이 부족해도 이 Topic은 가능한 한 남긴다.
중요도가 `LOW` 여도 이 값이 `true` 일 수 있다.

### 분석 결과는 통째로 교체된다

재분석 시 세션의 Topic을 모두 지우고 새로 넣는다. 개별 갱신을 하지 않으므로
Topic ID는 재분석마다 바뀐다. 화면에서 Topic ID를 오래 들고 있지 않는다.

## 구현된 API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/sessions/{sessionCode}/topics` | Topic 목록 (topicOrder 순) |

명세: `docs/api/analysis-api.md` (저장소 루트 기준)

## 아직 없는 것

```
Topic 상세 조회 API   만들지 않는다 (목록에 모든 정보가 있다)
Topic 수정 API        만들지 않는다 (분석 결과는 통째로 교체한다)
Curriculum / StudyStep  STEP 7
Quiz                    STEP 9
```
