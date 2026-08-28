# 배포 — 명령 한 줄

```bash
git clone https://github.com/05solar/kiro-test.git
cd kiro-test
cp .env.example .env        # GEMINI_API_KEY 채우기
docker compose up -d --build
```

브라우저에서 `http://<서버주소>` 를 연다.

자세한 점검 항목과 남은 제약은 [docs/deployment.html](docs/deployment.html) 참고.

---

## 구성

```
                    ┌─────────────────────────────┐
 브라우저 ──:80──▶ │ frontend   Next.js 16        │
                    │            /api/* 를 전달    │
                    └──────────┬──────────────────┘
                               │ 내부 네트워크
                    ┌──────────▼──────────────────┐
                    │ backend    Spring Boot       │
                    └──────────┬──────────────────┘
                               │
                    ┌──────────▼──────────────────┐
                    │ db         PostgreSQL 16     │
                    └─────────────────────────────┘
```

**밖으로 열리는 포트는 하나뿐이다.** 백엔드와 DB 는 내부 네트워크에만 붙어 있어
EC2 보안 그룹에서 80(또는 `PUBLIC_PORT`)만 열면 된다.

브라우저는 백엔드를 직접 부르지 않는다. 프론트가 `/api/*` 를 받아 전달하므로
CORS 설정이 필요 없고 백엔드 주소가 밖으로 드러나지 않는다.

## 기동 순서

`docker compose` 가 순서를 지킨다. 각 단계는 앞 단계가 **정상 응답할 때까지** 기다린다.

```
db 기동 → pg_isready 통과 → backend 기동 → /actuator/health 통과 → frontend 기동
```

스키마는 백엔드가 뜨면서 **Flyway 가 스스로 적용한다.** 사람이 먼저 psql 을 칠 일이 없다.

```
backend 기동 → Flyway 가 밀린 마이그레이션 적용 → Hibernate 가 결과를 검증 → 서비스 시작
```

순서가 이 안에 들어 있다는 것이 핵심이다. 예전에는 마이그레이션이 배포 **밖에** 있어서,
새 이미지를 먼저 올리면 컬럼이 없는 채로 기동해 Schema-validation 으로 죽었다.

## EC2 준비

```bash
# Amazon Linux 2023
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user     # 다시 로그인해야 적용된다
sudo dnf install -y docker-compose-plugin
```

| 항목 | 권장 |
| --- | --- |
| 인스턴스 | t3.small 이상 (빌드 시 메모리를 쓴다) |
| 디스크 | 20GB 이상 |
| 보안 그룹 | 인바운드 80 (또는 `PUBLIC_PORT`), 22 |

t3.micro 에서는 프론트 빌드가 메모리 부족으로 죽을 수 있다.
그런 경우 다른 곳에서 이미지를 만들어 ECR 에 올리고 EC2 에서는 받아 쓰기만 한다.

## 환경변수

저장소 루트의 `.env` 하나만 있으면 된다. `docker compose` 가 자동으로 읽는다.

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `GEMINI_API_KEY` | **필수** | 없으면 기동 시 바로 실패한다 |
| `PUBLIC_PORT` | `80` | 밖으로 열 포트 |
| `DB_PASSWORD` | `postgres` | 운영에서는 바꾼다 |
| `DB_NAME` / `DB_USERNAME` | `naeil_study` / `postgres` | |
| `APP_TIMEZONE` | `Asia/Seoul` | 시각 계산 기준 |
| `LOG_LEVEL` | `INFO` | 문제 추적 시 `DEBUG` |

`.env` 는 `.gitignore` 에 있다. 키가 저장소에 올라가지 않는다.

## 자주 쓰는 명령

```bash
docker compose ps                    # 상태
docker compose logs -f backend       # 로그
docker compose up -d --build         # 코드 변경 후 재배포
docker compose restart frontend      # 한 서비스만
docker compose down                  # 중지 (데이터는 남는다)
docker compose down -v               # 중지 + 데이터 삭제 ⚠
```

## 데이터

두 개의 볼륨에 남는다. `docker compose down` 으로는 지워지지 않는다.

```
db-data    PostgreSQL 데이터
uploads    업로드한 강의자료
```

컨테이너를 전부 다시 만들어도 세션과 파일이 유지되는 것을 확인했다.

⚠ `docker compose down -v` 는 두 볼륨을 **지운다.** 사용자 데이터가 사라진다.

## 확인

```bash
# 1. 세 컨테이너가 healthy 인가
docker compose ps

# 2. 시간대 — 이것부터 본다
docker compose logs backend | grep "timezone fixed"
→ application timezone fixed: Asia/Seoul

# 3. 화면
curl -o /dev/null -w "%{http_code}\n" http://localhost/
→ 200

# 4. API 프록시
curl -X POST http://localhost/api/sessions
→ {"sessionCode":"XXXXXXXX","status":"CREATED"}

# 5. 백엔드가 밖으로 안 열려 있는가
curl --max-time 3 http://localhost:8080/actuator/health
→ 연결 실패해야 정상
```

**2번이 핵심이다.** 이 값이 UTC 면 남은 학습 시간이 9시간 부풀려져
실행할 수 없는 계획이 만들어진다. 에러 없이 틀린 답이 나오므로 직접 확인한다.

## 스키마를 바꿨을 때

**배포 전에 할 일은 없다.** 마이그레이션 파일만 저장소에 넣어 두면 백엔드가 뜨면서 적용한다.

```
backend/src/main/resources/db/migration/
├── V1__initial_schema.sql       최초 스키마
├── V2__general_knowledge.sql    exam_scope, source_type
├── V3__study_chat.sql           chat_messages
└── V4__chat_message_order.sql   대화 순서 컬럼 (message_order)
```

엔티티에 필드를 더했다면 `V5__…sql` 을 같은 커밋에 넣는다. 빠뜨리면 `ddl-auto=validate`
가 기동을 막는데, **그 실패는 내 컴퓨터에서 먼저 난다** — 로컬도 같은 설정이기 때문이다.

```bash
# 로컬에서 확인. 컨테이너를 새로 띄우면 마이그레이션이 다시 돈다.
docker compose up -d --build backend
docker compose logs backend | grep -i flyway
```

### 규칙 세 가지

**1. 이미 적용된 파일을 고치지 않는다.** Flyway 는 각 파일의 체크섬을 이력 표에 남긴다.
내용이 바뀌면 다음 기동이 `Migration checksum mismatch` 로 멈춘다. 이미 나간 버전을
고쳐야 하면 새 번호로 파일을 하나 더 만든다.

**2. 멱등하게 쓴다.** `ADD COLUMN IF NOT EXISTS`, `CREATE TABLE IF NOT EXISTS`,
제약은 `DO $$ ... IF NOT EXISTS ... $$`. Flyway 도입 전에 손으로 맞춰 둔 DB 는 V1 로
baseline 된 뒤 V2 부터 **다시 실행되기 때문**이다. 멱등하지 않으면 그 서버만 기동에 실패한다.

**3. 되돌리는 마이그레이션은 쓰지 않는다.** 컬럼을 지우는 변경은 데이터를 지우는 변경이다.
꼭 필요하면 두 번에 나눠 배포한다 — 먼저 코드에서 쓰지 않게 하고, 다음 배포에서 지운다.

### 이미 떠 있는 DB 는 어떻게 되나

Flyway 이력 표(`flyway_schema_history`)가 없는 DB 를 만나면 V1 이 적용된 것으로 표시하고
V2 부터 이어서 적용한다(`baseline-on-migrate`). 그래서 지금 EC2 에 떠 있는 DB 도,
손으로 마이그레이션을 적용해 둔 DB 도, 아무것도 하지 않고 새 버전을 올리면 된다.

```bash
docker compose exec db psql -U postgres -d naeil_study \
  -c "select version, description, success from flyway_schema_history order by installed_rank;"
```

### docs/schema.sql 은 이제 참고용이다

현재 스키마의 스냅샷일 뿐, 어디에도 자동 적용되지 않는다. 적용 경로는 Flyway 하나다.
엔티티를 고쳤으면 갱신해 두면 좋지만, 잊어도 기동에는 영향이 없다.

```bash
docker compose exec db pg_dump -U postgres -d naeil_study \
  --schema-only --no-owner --no-privileges > docs/schema.sql
```

### 확인

```bash
bash scripts/migration-verify.sh
```

빈 DB / 구버전 스키마 / 최신 스키마 / 재기동 네 경우로 실제 컨테이너를 띄워 본다.
두 번째가 예전에 죽던 경우다.

## 알려진 제약

| 항목 | 내용 |
| --- | --- |
| **인스턴스 1대 전용** | 업로드 파일이 로컬 볼륨에 있다. 여러 대로 늘리려면 S3 구현이 필요하다 |
| **AI 요청이 동기** | 앞에 ALB 를 둔다면 유휴 타임아웃을 300초로 올린다. 기본 60초로는 분석이 끊긴다 |
| **HTTPS 없음** | 인증서는 ALB 나 Caddy/nginx 같은 리버스 프록시에서 끊는다 |
