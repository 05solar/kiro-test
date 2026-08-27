# document/controller — 에이전트 작업 규칙

## 지킬 것

- 컨트롤러는 얇게 유지한다. 매핑과 변환만 한다.
- 업로드는 201, 삭제는 204를 명시한다.
- consumes = MULTIPART_FORM_DATA_VALUE 를 명시해 잘못된 Content-Type 을 걸러낸다.
- 모든 경로를 /api/sessions/{sessionCode} 아래에 둔다.

## 하지 말 것

- 파일 검증이나 저장 로직을 컨트롤러에 넣기
- /api/documents/{id} 처럼 세션 없는 경로 추가
- 파일 다운로드 엔드포인트 추가
- MultipartFile 을 응답에 노출하거나 로그로 출력
- 컨트롤러에서 예외를 잡아 직접 응답 만들기
