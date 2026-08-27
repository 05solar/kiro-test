# document/validation

| 클래스 | 역할 |
| --- | --- |
| `DocumentFileValidator` | 업로드 요청 전체 검증 |
| `DocumentFileValidator.ValidatedFile` | 검증을 마친 파일 (파일 + 정규화된 이름 + 형식) |

## 왜 별도 클래스인가

업로드 검증은 조건이 많고, 이 조건들이 곧 사용자에게 보여줄 에러 메시지가 된다.
서비스에 섞어 두면 저장 흐름과 규칙이 뒤엉킨다.

## 검증 순서

```
1. 파일이 하나라도 있는가            EMPTY_FILE
2. 개수 한도 (요청 + 기존 저장분)     FILE_COUNT_EXCEEDED
3. 파일별
   - 빈 파일인가                     EMPTY_FILE
   - 20MB 초과인가                   FILE_SIZE_EXCEEDED
   - 파일명이 남는가                 UNSUPPORTED_FILE_TYPE
   - 확장자가 허용 목록에 있는가      UNSUPPORTED_FILE_TYPE
   - MIME Type이 어긋나는가          UNSUPPORTED_FILE_TYPE
4. 총 용량 한도 (요청 + 기존 저장분)  SESSION_STORAGE_EXCEEDED
```

## 저장 전에 전부 검증한다

한 요청은 전체 성공하거나 전체 실패한다.
그래서 파일을 하나씩 저장하면서 검사하지 않고, 모두 검증한 뒤 저장을 시작한다.
세 번째 파일이 잘못됐다고 해서 앞의 두 개가 저장되어 있으면 안 된다.

## 반환값

검증을 통과하면 ValidatedFile 목록을 돌려준다.
서비스는 사용자 입력 파일명을 다시 해석하지 않고 이 값만 쓴다.
