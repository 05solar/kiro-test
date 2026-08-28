# scripts

띄워 놓은 스택에 대고 돌리는 검증 스크립트. 자동 테스트가 잡지 못하는 것,
즉 **실제로 배포된 모양 그대로 동작하는지**를 확인한다.

| 스크립트 | 확인하는 것 |
| --- | --- |
| `gk-verify.sh` | 자료 없이 시험 범위만으로 주제·계획·퀴즈가 만들어지는지 |
| `chat-verify.sh` | 학습 챗봇이 근거를 사실대로 표시하고 대화를 저장하는지 |
| `migration-verify.sh` | DB 가 어떤 상태든 기동이 죽지 않는지 (빈 DB / 구버전 / 최신 / 재기동) |
| `live-gemini.sh` | **실제 Gemini 를 5회 부른다.** 함부로 돌리지 않는다 |

## 실행

스택이 떠 있어야 한다. 요청은 전부 프론트엔드의 `/api` 프록시를 지난다 —
백엔드 포트를 밖으로 열지 않으므로 실제 사용자와 같은 경로로 확인하게 된다.

```bash
LLM_MODE=mock QUIZ_AI_MODE=mock PUBLIC_PORT=8090 docker compose up -d --build
bash scripts/gk-verify.sh    # 실제 AI 를 부르지 않는다
bash scripts/chat-verify.sh
bash scripts/migration-verify.sh
```

**`LLM_MODE=mock` 으로 돌린다.** 기능이 이어지는지는 목 데이터로 전부 확인할 수 있고,
한 번 돌릴 때마다 과금되면 안 되기 때문이다. 실제 AI 호출 검증은 따로, 최소 횟수만 한다.

## 왜 자동 테스트로 대신하지 않는가

여기서 잡히는 것은 컨테이너·프록시·스키마처럼 **JVM 밖에 있는 것들**이다.
실제로 이 스크립트가 잡은 것:

- 운영 프로파일(`ddl-auto=validate`)에서 컬럼을 추가하고 마이그레이션을 적용하지 않으면
  기동 중 죽는다 → `docs/migrations/` 신설
- `PUT /exam` 응답에 저장한 시험 범위가 빠져 있었다 → `ExamResponse` 수정

결과는 `docs/general-knowledge-test.md` 에 남긴다.

## live-gemini.sh 만 예외다

이 스크립트만 실제 AI 를 부른다(5회). 무료 사용량을 아껴야 하므로 **함부로 돌리지 않는다.**
무엇에 몇 번 쓰는지는 스크립트 첫머리에 적어 두었다.

```bash
LLM_MODE=gemini PUBLIC_PORT=8090 docker compose up -d
bash scripts/live-gemini.sh <자료가-추출된-세션코드>
LLM_MODE=mock QUIZ_AI_MODE=mock PUBLIC_PORT=8090 docker compose up -d   # 곧바로 되돌린다
```

되돌리는 것을 잊으면 화면을 열어 볼 때마다 과금된다. 결과는
`docs/live-gemini-test.md` 에 남긴다.
