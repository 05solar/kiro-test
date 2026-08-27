# studycontext/dto — 에이전트 작업 규칙

## 지킬 것

- `record` 로 만든다. 변환은 정적 팩터리에 둔다.
- 값이 없으면 `null` 로 내려보낸다. 키를 생략하지 않는다.
- 길이 제한 값은 `StudyContextPolicy.MAX_FIELD_LENGTH` 를 참조한다. 숫자를 복사하지 않는다.
- 검증 메시지에 어느 항목인지 드러낸다.

## 하지 말 것

- `@NotBlank` / `@NotNull` 추가 (네 항목 모두 선택 입력이다)
- `@JsonInclude(NON_NULL)` 추가 (키가 사라져 프론트 초기화가 깨진다)
- 요청 DTO에서 정규화 수행 (서비스의 몫이다)
- 엔티티를 그대로 반환
