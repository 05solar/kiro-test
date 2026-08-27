# analysis/dto

| 클래스 | 방향 | 쓰이는 곳 |
| --- | --- | --- |
| `AnalysisResponse` | 응답 | `POST .../analysis` |

## AnalysisResponse

```json
{
  "sessionCode": "7K2M9QXF",
  "status": "READY",
  "topicCount": 6
}
```

동기 처리라 응답 시점에 분석이 끝나 있다. 그래서 진행 중 상태가 아니라 최종 상태를 담는다.

Topic 내용은 담지 않는다. 목록은 `GET .../topics` 로 조회한다.
분석 응답에 Topic 전체를 넣으면 같은 데이터를 두 경로로 내보내게 되고,
한쪽을 고칠 때 다른 쪽을 빠뜨린다.

## AI 요청/응답 모델은 여기 없다

`analysis/client/dto` 에 있다. 이 패키지는 **HTTP 응답**만 담는다.
AI와 주고받는 형태와 사용자에게 보여주는 형태를 섞지 않는다.
