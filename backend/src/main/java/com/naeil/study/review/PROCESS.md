# review — 작업 절차

## 이 패키지를 고칠 때

1. **정답 노출 규칙부터 본다.** `quiz` 의 `QuizReviewResponse` 는 답한 문제에만
   `correctIndex` 와 `explanation` 을 담는다. 이 패키지는 그 변환을 그대로 쓴다.
   여기서 따로 정답을 채워 넣지 않는다. `QuizReviewResponseTest` 가 그것을 못박는다.
2. **마지막 회차만 담는 규칙을 건드리지 않는다.** 회차를 합치면 정리에 같은 범위의
   문제가 두 벌 나온다. `SessionReviewServiceTest > 회차가 여러 개면 마지막 회차만 담는다`.
3. **완료 판정에서 `SKIPPED` 를 남은 것으로 세지 않는다.** 그렇게 하면 시간이 모자라
   잘라 낸 STEP 때문에 영영 완료가 되지 않는다.
   `SessionReviewServiceTest > 건너뛴 스텝이 있어도 나머지를 마쳤으면 완료다`.
4. `docs/api/review-api.md` 를 같은 커밋에서 고친다. 어긋나면 문서가 틀린 것으로 본다.
5. `./gradlew test --tests "com.naeil.study.review.*"` 로 확인한다. 전체 실행 전에 여기부터.

## 담을 것을 늘리고 싶을 때

이 응답은 이미 세션의 문제 전부를 담는다. 한 세션에 STEP 열 개, STEP 당 문제 다섯 개면
문제 오십 개가 한 번에 나간다. 필드를 더하기 전에 두 가지를 묻는다.

- **세 갈래(요약 / 푼 문제 / 틀린 문제) 중 어디가 쓰는가.** 아무 데도 안 쓰면 넣지 않는다.
- **한 STEP 만 필요한 것인가.** 그렇다면 `quiz` 의 `/quiz-review` 쪽이다.

## 새 도메인을 읽어 오고 싶을 때

`review` 가 다른 도메인을 아는 것은 괜찮다 — 그러라고 만든 자리다. 반대는 안 된다.
`curriculum` 이나 `quiz` 가 `review` 를 import 하기 시작하면 순환이 생긴다.

읽기 전용이다. 이 패키지는 아무것도 쓰지 않는다. 상태를 바꾸는 코드를 넣지 않는다.

## 성능

STEP 마다 질의하면 N+1 이다. 세션의 퀴즈와 답안을 한 번에 읽고 메모리에서 짝지운다.
`findAllBySessionIdWithTopic` 은 Topic 을 함께 가져오는 질의다 — 이것을
`findAll` 로 바꾸면 STEP 마다 Topic 을 지연 로딩하게 된다.

## 검증 순서

```
1. ./gradlew test --tests "com.naeil.study.review.*"   빠르게 이 기능만
2. ./gradlew test                                      다른 기능을 깨뜨리지 않았는지
3. 컨테이너에서 실제 세션으로 GET /review 호출          실제 데이터 모양 확인
```

3번이 필요한 이유는 이 응답이 여러 도메인의 실제 데이터 조합에 달려 있기 때문이다.
목 데이터로는 "Topic 이 없는 복습 STEP", "회차가 두 개인 Topic", "안 푼 문제가 섞인
STEP" 같은 조합이 실제로 어떻게 나오는지 확인되지 않는다.

```bash
docker compose exec -T db psql -U postgres -d naeil_study -t -A -F'|' \
  -c "select s.session_code, count(distinct st.id), count(distinct r.id)
      from study_sessions s
      join curriculums c on c.session_id = s.id
      join study_steps st on st.curriculum_id = c.id
      left join quizzes q on q.topic_id = st.topic_id
      left join quiz_results r on r.quiz_id = q.id
      group by s.session_code order by 3 desc limit 5;"

curl -s "http://localhost/api/sessions/{코드}/review" | head -c 800
```

## 새 필드를 엔티티에 더할 때

이 패키지에는 엔티티가 없다. 정리에 새 값이 필요하면 그 값을 가진 도메인
(`topic`, `curriculum`, `quiz`)에 더하고, 여기서는 읽기만 한다.
