# document/entity 작업 절차

## 필드를 추가할 때

```
1. 필드 + @Column(name = "snake_case")
2. Javadoc 에 언제 채워지고 누가 바꾸는지 적는다
3. DocumentRepositoryTest 에 영속화 확인 추가
4. 응답에 노출할 값인지 판단한다 (저장 경로 계열은 노출하지 않는다)
5. docs/database.md 컬럼 표 갱신
```

## STEP 4-2 에서 추가할 것

```
extractedText   TEXT nullable   추출한 텍스트
startParsing()                  UPLOADED → PARSING
markParsed(text)                PARSING → PARSED + 텍스트 저장
markParseFailed()               PARSING → PARSE_FAILED
```

상태 전이 메서드에서 잘못된 전이는 예외로 막는다.

## 검증

```bash
./gradlew test --tests "*DocumentPolicyTest*" --tests "*DocumentRepositoryTest*"
```
