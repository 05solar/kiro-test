# session/entity — 에이전트 작업 규칙

## 지킬 것

- 세터를 만들지 않는다.
- 생성은 정적 팩터리로만 한다. `public` 생성자를 열지 않는다.
- 시각은 파라미터로 받는다. `LocalDateTime.now()`, `@PrePersist`, `@CreatedDate` 모두 쓰지 않는다.
  만료 정책이 핵심 로직이라 테스트에서 시간을 고정할 수 있어야 한다.
- 컬럼명을 `@Column(name = ...)` 로 명시한다.
- `sessionCode` 는 `updatable = false` 를 유지한다. 발급된 코드는 바뀌지 않는다.

## 코드 문자 집합

`SessionCodePolicy.ALPHABET` 문자열을 다른 파일에 복사하지 않는다.
생성기, 검증, 테스트, 문서 모두 이 상수를 참조한다.

## 학습 시간 필드

```
availableStudyMinutes  절대 감소시키지 않는다
remainingStudyMinutes  STEP 완료 시에만 감소한다 (STEP 7 구현 예정)
```

두 값을 하나로 합치자는 리팩터링을 하지 않는다. 최초 계획 대비 초과/단축을 계산하려면 둘 다 필요하다.

## 하지 말 것

- `@Data`, `@Setter`, `@AllArgsConstructor` 사용
- Enum 을 `@Enumerated(EnumType.ORDINAL)` 로 저장 (순서가 바뀌면 데이터가 깨진다)
- 엔티티에 JSON 애노테이션 추가 (직렬화는 DTO의 몫이다)
