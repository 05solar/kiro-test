# session/exception

세션 도메인 예외. 모두 BusinessException 을 상속하고 ErrorCode 를 갖는다.

| 예외 | HTTP | code |
| --- | --- | --- |
| `InvalidSessionCodeException` | 400 | `INVALID_SESSION_CODE` |
| `InvalidExamTimeException` | 400 | `INVALID_EXAM_TIME` |
| `SessionNotFoundException` | 404 | `SESSION_NOT_FOUND` |
| `SessionCodeGenerationException` | 500 | `SESSION_CODE_GENERATION_FAILED` |

## InvalidSessionCodeException

세션 코드 형식이 규칙에 맞지 않을 때. DB를 조회하기 전에 던진다.
무작위 문자열로 DB를 두드리는 요청을 걸러낸다.

## SessionNotFoundException

해당 코드의 세션이 없을 때.

만료되어 삭제된 세션도 같은 예외를 쓴다. 존재 여부의 차이가 응답에 드러나면
코드 추측에 힌트가 되기 때문이다.

## SessionCodeGenerationException

최대 재시도 횟수 안에 중복되지 않는 코드를 만들지 못했을 때.
정상 상황에서는 발생하지 않는다. 발생한다면 코드 공간이 포화되었거나
중복 검사 경로에 문제가 있다는 신호다. 진단을 위해 attempts 를 함께 들고 있다.
