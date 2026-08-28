#!/usr/bin/env bash
# 스키마 마이그레이션 검증. 실제 AI 를 부르지 않는다(LLM_MODE=mock).
#
# EC2 에 올릴 때 기동이 죽는 경우는 대부분 "DB 가 어떤 상태였는가"에서 갈린다.
# 그래서 상태를 셋으로 나눠 각각 실제로 띄워 본다.
#
#   A. 빈 DB          처음 올리는 서버. Flyway 가 마이그레이션을 전부 적용해야 한다
#   B. 구버전 스키마   이전 릴리스가 돌던 서버. baseline 후 나머지가 적용돼야 한다
#   C. 최신 스키마     Flyway 도입 전에 손으로 맞춰 둔 서버. 아무 일도 없어야 한다
#   D. 재기동          이미 최신인 DB. 두 번째 기동도 그대로 떠야 한다
#
# B 가 실제로 죽었던 경우다. 새 이미지를 먼저 올리고 마이그레이션을 나중에 하면
# Schema-validation 으로 기동에 실패했다.
#
# 검사가 실패하면 제품보다 이 스크립트를 먼저 의심한다. 처음 돌렸을 때 실패 4건이
# 전부 스크립트 문제였다 — 경로가 깨져 빈 DB 를 "구버전"이라 부르며 검사하고 있었다.
#
# 테스트는 H2 로 돌기 때문에 마이그레이션 파일의 정확성을 잡아 주지 못한다.
# 이 스크립트가 그 자리를 메운다.
set -u

# 셸 리다이렉션(< 파일)으로만 쓰므로 MSYS 경로 그대로면 된다.
# pwd -W 를 섞으면 두 줄이 이어져 경로가 깨진다 — 실제로 그렇게 깨뜨렸다.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NETWORK="${NETWORK:-naeil_default}"
IMAGE="${IMAGE:-naeil-backend:latest}"
DB_CONTAINER="${DB_CONTAINER:-naeil-db-1}"
PGUSER="${PGUSER:-postgres}"

pass=0; fail=0
check() { # check <설명> <상태코드>
  if [ "$2" = "0" ]; then echo "  [PASS] $1"; pass=$((pass+1));
  else echo "  [FAIL] $1"; fail=$((fail+1)); fi
}

psqlq() { docker exec -i "$DB_CONTAINER" psql -U "$PGUSER" -d "${2:-postgres}" -tAc "$1"; }

recreate_db() { # recreate_db <이름>
  docker exec -i "$DB_CONTAINER" psql -U "$PGUSER" -d postgres -q \
    -c "drop database if exists $1 with (force);" -c "create database $1;" > /dev/null
}

# 백엔드를 한 번 띄워 보고, 떴으면 0 / 죽었으면 1 을 돌려준다.
# 로그는 $LOGFILE 에 남긴다.
LOGFILE=""
boot_backend() { # boot_backend <db이름> <컨테이너이름>
  local db="$1" name="$2"
  LOGFILE="/tmp/mig-$name.log"
  docker rm -f "$name" > /dev/null 2>&1
  docker run -d --name "$name" --network "$NETWORK" \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e DB_URL="jdbc:postgresql://db:5432/$db" \
    -e DB_USERNAME="$PGUSER" \
    -e DB_PASSWORD="${PGPASSWORD:-postgres}" \
    -e GEMINI_API_KEY=not-used-in-mock-mode \
    -e LLM_MODE=mock \
    -e STORAGE_ROOT_PATH=/tmp/uploads \
    -e LOG_LEVEL=INFO \
    "$IMAGE" > /dev/null 2>&1

  # 성공/실패 어느 쪽이든 로그에 흔적이 남는다. 둘 다 기다린다.
  local waited=0
  while [ "$waited" -lt 120 ]; do
    docker logs "$name" > "$LOGFILE" 2>&1
    if grep -q "Started StudyBackendApplication" "$LOGFILE"; then
      docker rm -f "$name" > /dev/null 2>&1
      return 0
    fi
    if grep -qE "APPLICATION FAILED TO START|SchemaManagementException|FlywayException|Migration .* failed" "$LOGFILE"; then
      docker rm -f "$name" > /dev/null 2>&1
      return 1
    fi
    if [ -z "$(docker ps -q -f name="^${name}$")" ]; then
      docker logs "$name" > "$LOGFILE" 2>&1
      docker rm -f "$name" > /dev/null 2>&1
      return 1
    fi
    sleep 2
    waited=$((waited + 2))
  done
  docker logs "$name" > "$LOGFILE" 2>&1
  docker rm -f "$name" > /dev/null 2>&1
  return 1
}

# 이력을 "버전=종류" 로 늘어놓는다.
#
# 버전 번호만 보면 안 된다. baseline 도 이력에 한 줄로 남기 때문에, 실행된 것과
# 건너뛴 것이 똑같이 "1" 로 보인다. 구분되는 것은 종류다.
#
#   BASELINE  건너뛰었다. 이미 그 상태였다고 표시만 한 것
#   SQL       실제로 실행했다
applied_versions() { # applied_versions <db이름>
  psqlq "select string_agg(version || '=' || type, ',' order by installed_rank) \
         from flyway_schema_history where success;" "$1" | tr -d '[:space:]'
}

# 마이그레이션 파일을 DB 에 직접 넣는다(Flyway 도입 전 상태를 흉내 낸다).
#
# 실패하면 바로 멈춘다. 조용히 넘어가면 빈 DB 를 "구버전 스키마"라고 부르며 검사하게 되고,
# 그 검사는 전부 통과한다 — 아무것도 확인하지 않은 채로. 실제로 그렇게 한 번 속았다.
seed() { # seed <db이름> <마이그레이션파일명>
  local file="$ROOT/backend/src/main/resources/db/migration/$2"
  if [ ! -f "$file" ]; then
    echo "  [ERROR] 마이그레이션 파일을 찾을 수 없다: $file"; exit 1
  fi
  if ! docker exec -i "$DB_CONTAINER" psql -U "$PGUSER" -d "$1" -q -v ON_ERROR_STOP=1 \
      < "$file" > /tmp/seed.log 2>&1; then
    echo "  [ERROR] $2 적용 실패:"; sed 's/^/          /' /tmp/seed.log; exit 1
  fi
}

echo "== A. 빈 DB — 처음 올리는 서버 =="
recreate_db mig_empty
boot_backend mig_empty mig-a; check "A1 기동 성공" $?
V=$(applied_versions mig_empty)
[ "$V" = "1=SQL,2=SQL,3=SQL,4=SQL" ]; check "A2 V1 부터 전부 실제로 실행됐다 (=$V)" $?
T=$(psqlq "select count(*) from information_schema.tables where table_schema='public' and table_name in ('study_sessions','chat_messages');" mig_empty | tr -d '[:space:]')
[ "$T" = "2" ]; check "A3 최신 테이블이 만들어졌다" $?

echo
echo "== B. 구버전 스키마 — 이전 릴리스가 돌던 서버 (실제로 죽었던 경우) =="
recreate_db mig_old
# Flyway 도입 전 상태를 만든다. 이력 표는 없고 테이블만 있다.
seed mig_old V1__initial_schema.sql
H=$(psqlq "select count(*) from information_schema.tables where table_schema='public' and table_name='flyway_schema_history';" mig_old | tr -d '[:space:]')
[ "$H" = "0" ]; check "B0 이력 표가 없는 상태로 준비됐다" $?
C=$(psqlq "select count(*) from information_schema.columns where table_name='study_sessions' and column_name='exam_scope';" mig_old | tr -d '[:space:]')
[ "$C" = "0" ]; check "B1 exam_scope 가 아직 없다" $?

boot_backend mig_old mig-b; check "B2 기동 성공 — 사람이 psql 을 치지 않았는데 떴다" $?
V=$(applied_versions mig_old)
[ "$V" = "1=BASELINE,2=SQL,3=SQL,4=SQL" ]; check "B3 V1 은 건너뛰고 V2 부터 실행했다 (=$V)" $?
C=$(psqlq "select count(*) from information_schema.columns where table_name='study_sessions' and column_name='exam_scope';" mig_old | tr -d '[:space:]')
[ "$C" = "1" ]; check "B4 exam_scope 가 생겼다" $?
C=$(psqlq "select count(*) from information_schema.tables where table_schema='public' and table_name='chat_messages';" mig_old | tr -d '[:space:]')
[ "$C" = "1" ]; check "B5 chat_messages 가 생겼다" $?

echo
echo "== C. 최신 스키마 — 손으로 맞춰 둔 서버 =="
recreate_db mig_current
for f in V1__initial_schema.sql V2__general_knowledge.sql V3__study_chat.sql V4__chat_message_order.sql; do
  seed mig_current "$f"
done
boot_backend mig_current mig-c; check "C1 기동 성공 — 멱등이라 다시 적용해도 깨지지 않는다" $?
V=$(applied_versions mig_current)
[ "$V" = "1=BASELINE,2=SQL,3=SQL,4=SQL" ]; check "C2 V1 은 건너뛰고 나머지는 멱등하게 다시 돌았다 (=$V)" $?

echo
echo "== D. 재기동 — 이미 최신인 DB =="
boot_backend mig_empty mig-d; check "D1 두 번째 기동도 성공" $?
V=$(applied_versions mig_empty)
[ "$V" = "1=SQL,2=SQL,3=SQL,4=SQL" ]; check "D2 이력이 늘어나지 않았다 (=$V)" $?

echo
echo "== 정리 =="
for db in mig_empty mig_old mig_current; do
  docker exec -i "$DB_CONTAINER" psql -U "$PGUSER" -d postgres -q \
    -c "drop database if exists $db with (force);" > /dev/null 2>&1
done
echo "  임시 DB 삭제 완료"

echo
echo "결과: PASS=$pass FAIL=$fail"
[ "$fail" = "0" ] || echo "마지막 실패 로그: $LOGFILE"
