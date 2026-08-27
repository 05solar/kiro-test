# topic/dto

| 클래스 | 방향 | 쓰이는 곳 |
| --- | --- | --- |
| `TopicResponse` | 응답 | Topic 한 건 |
| `TopicListResponse` | 응답 | `GET .../topics` |

## TopicResponse

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "CPU 스케줄링",
  "summary": "...",
  "keyPoints": ["FCFS", "SJF", "Round Robin"],
  "importance": "VERY_HIGH",
  "estimatedStudyMinutes": 35,
  "professorEmphasisMatched": false,
  "pastExamMatched": true,
  "weakAreaMatched": false,
  "mustStudyMatched": false,
  "topicOrder": 2
}
```

`sourceDocumentIds` 는 담지 않는다. 화면에서 쓰지 않는 내부 추적 정보다.

## importance는 문자열로 내보낸다

숫자 점수가 아니라 Enum 이름이다. 프론트가 "매우 중요 / 중요 / 보통 / 낮음" 으로 옮긴다.
서버가 화면 문구를 정하지 않는다.
