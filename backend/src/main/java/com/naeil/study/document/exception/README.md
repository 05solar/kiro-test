# document/exception

| 예외 | HTTP | code |
| --- | --- | --- |
| `EmptyFileException` | 400 | `EMPTY_FILE` |
| `UnsupportedFileTypeException` | 400 | `UNSUPPORTED_FILE_TYPE` |
| `FileSizeExceededException` | 400 | `FILE_SIZE_EXCEEDED` |
| `FileCountExceededException` | 400 | `FILE_COUNT_EXCEEDED` |
| `SessionStorageExceededException` | 400 | `SESSION_STORAGE_EXCEEDED` |
| `DocumentNotFoundException` | 404 | `DOCUMENT_NOT_FOUND` |
| `DocumentAlreadyParsingException` | 409 | `DOCUMENT_ALREADY_PARSING` |
| `DocumentParseFailedException` | 422 | `DOCUMENT_PARSE_FAILED` |
| `NoExtractableTextException` | 422 | `NO_EXTRACTABLE_TEXT` |

저장소 오류(FileStorageException, 500)는 storage/exception 에 있다.

## DocumentNotFoundException 이 404인 이유

다른 세션 소유의 문서를 요청한 경우에도 이 예외를 쓴다.
403으로 응답하면 그 ID의 문서가 어딘가에 존재한다는 사실이 새어 나간다.
없는 문서와 남의 문서를 구분하지 않는다.

## 개수/용량 예외가 400인 이유

사용자가 파일을 지우면 해결할 수 있는 상황이다. 서버 잘못이 아니므로 400으로 응답하고,
메시지에 한도를 적어 무엇을 하면 되는지 알려준다.
