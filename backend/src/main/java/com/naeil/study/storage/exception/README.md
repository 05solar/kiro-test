# storage/exception

| 예외 | HTTP | code |
| --- | --- | --- |
| `FileStorageException` | 500 | `FILE_STORAGE_FAILED` |

파일 저장소의 읽기/쓰기/삭제가 실패했을 때 쓴다.
경로 이탈처럼 있어서는 안 되는 요청에도 같은 예외를 쓴다.

원인 예외는 `initCause` 로 붙여 로그에만 남긴다.
서버 경로, OS 오류 메시지, 스택트레이스는 응답에 넣지 않는다.

```json
{
  "code": "FILE_STORAGE_FAILED",
  "message": "파일 저장 중 오류가 발생했습니다."
}
```
