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

스키마는 DB 가 처음 뜰 때 `docs/schema.sql` 이 자동으로 적용된다.
백엔드는 운영 프로파일에서 `ddl-auto=validate` 라 스키마를 만들지 않는다.

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

엔티티를 고치면 `docs/schema.sql` 을 다시 뽑아야 한다. 안 하면 `validate` 가 기동을 막는다.

```bash
docker compose exec db pg_dump -U postgres -d naeil_study \
  --schema-only --no-owner --no-privileges > docs/schema.sql
```

`schema.sql` 은 **DB 볼륨이 비어 있을 때만** 자동 적용된다.
이미 데이터가 있는 DB 에는 변경분을 직접 적용해야 한다.

## 알려진 제약

| 항목 | 내용 |
| --- | --- |
| **인스턴스 1대 전용** | 업로드 파일이 로컬 볼륨에 있다. 여러 대로 늘리려면 S3 구현이 필요하다 |
| **AI 요청이 동기** | 앞에 ALB 를 둔다면 유휴 타임아웃을 300초로 올린다. 기본 60초로는 분석이 끊긴다 |
| **HTTPS 없음** | 인증서는 ALB 나 Caddy/nginx 같은 리버스 프록시에서 끊는다 |
