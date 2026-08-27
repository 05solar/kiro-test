# analysis

강의자료와 학습 맥락을 AI로 분석해 Topic을 만든다.

```
analysis
├── controller/   분석 실행 API
├── service/      오케스트레이션 + 짧은 트랜잭션
├── client/       AI 호출 추상화 + Claude 구현 + 요청/응답 모델
├── chunk/        문서 조각 나누기
├── prompt/       프롬프트 조립
├── validation/   AI 응답 검증
├── config/       AI 클라이언트 설정
├── dto/          응답 record
└── exception/    도메인 예외
```

## 두 단계로 나눈 이유

```
문서별 조각 → 1차 분석 (주제 후보) → 최종 통합 1회 → Topic
```

여러 문서를 한 문자열로 붙여 보내면 컨텍스트 한도를 넘긴다.
그렇다고 조각마다 최종 Topic을 만들면 같은 주제가 여러 개로 쪼개진다.

```
Chunk 1  CPU Scheduling
Chunk 4  CPU 스케줄링
Chunk 7  CPU Scheduling Algorithms
```

그래서 조각에서는 후보만 뽑고, 합치는 판단은 전체를 한 번에 보는 통합 단계에서 한다.
중요도와 학습시간도 통합 단계에서 정한다. 조각 하나만으로는 전체에서의 우선순위를 알 수 없다.

## 남은 시간을 쓰지 않는다

이 단계는 "무엇을 배울 수 있는가"를 정한다. "무엇을 배울 시간이 있는가"는 커리큘럼 단계다.
시간이 부족하다고 여기서 Topic을 빼면 두 판단이 뒤엉킨다.

```
Topic 전체 예상시간  520분
남은 학습시간        180분   →  그대로 저장한다
```

## Grounding

강의자료에 없는 내용을 새로운 학습 내용으로 만들지 않는다.
학습 맥락은 사용자가 준 힌트이지 검증된 사실이 아니다.

```
mustStudyAreas = "B+ Tree"
자료에 B+ Tree 없음  →  Topic을 만들지 않는다
```

## 프롬프트 주입 대비

시스템 지시문과 분석 대상 데이터를 한 문자열로 이어붙이지 않는다.
자료와 학습 맥락은 태그로 감싸고, 그 안의 내용은 명령이 아니라는 점을 시스템 프롬프트에 명시한다.

## AI 공급자 교체

도메인 서비스는 `AiAnalysisClient` 인터페이스만 안다.
공급자를 바꾸려면 구현체를 하나 더 만들고 `config/AiClientConfig` 에서 고르면 된다.

## 구현된 API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/sessions/{sessionCode}/analysis` | 분석 실행 (동기) |

명세: `docs/api/analysis-api.md` (저장소 루트 기준)

## 아직 없는 것

```
비동기 처리 / 진행률 API   현재는 동기. 구조는 옮길 수 있게 분리해 두었다
Embedding / Vector DB / RAG  계획 없음
Curriculum / StudyStep       STEP 7
Quiz                         STEP 9
```
