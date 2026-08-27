# analysis/validation

AI 응답 검증.

| 클래스 | 역할 |
| --- | --- |
| `AiTopicResponseValidator` | 응답 검증 + 보정 |
| `ValidatedTopic` | 저장 가능한 형태로 확정된 Topic |

## 구조화 출력을 써도 검증이 필요하다

구조화 출력은 **형태**를 보장한다. 값의 범위와 참조 무결성은 보장하지 않는다.
`estimatedStudyMinutes: 500`, `sourceDocuments: ["DOC_99"]` 같은 응답이 형식상으로는 정상이다.

## 두 갈래 규칙

### 실패 (분석 전체를 실패시킨다)

```
topics 가 비었다
title / summary 가 비었다
keyPoints 가 비었다
importance 가 정해진 값이 아니다
estimatedStudyMinutes 가 없다
```

구조가 깨진 응답은 다시 받아야 한다.

### 보정 (로그만 남기고 진행한다)

```
title 200자 초과            → 자른다
estimatedStudyMinutes 범위  → 5~120 으로 맞춘다
keyPoints 중복              → 순서를 유지하며 제거한다
모르는 문서 참조             → 버린다
Topic 수 상한 초과           → 앞에서부터 상한까지 남긴다
matched 값 없음             → false 로 본다
```

값이 조금 벗어난 것까지 전체 실패로 만들면 분석 한 번에 드는 비용과 시간을 매번 버린다.

## 문서 참조 되돌리기

AI가 돌려준 `DOC_1` 을 실제 문서 UUID로 바꾼다. 모르는 값은 버린다.
전부 버려져 비어도 실패로 보지 않는다. 출처 추적은 부가 정보이고,
그 때문에 분석 전체를 버리면 얻는 것보다 잃는 것이 크다.
