#!/usr/bin/env bash
# 1단계(일반 지식 기반 생성) 검증. LLM_MODE=mock 이므로 실제 AI 를 부르지 않는다.
#
# 두 가지 함정을 피해 둔다.
#  - 한글 본문은 파일에 UTF-8 로 써서 --data-binary 로 보낸다. 셸 인자로 직접 넘기면
#    Windows 콘솔 코드페이지에서 깨져 400 INVALID_REQUEST 가 난다.
#  - 작업 디렉터리는 Windows 경로로 둔다. mingw curl 은 -F "@/tmp/..." 를 열지 못한다(26).
set -u
BASE="http://localhost:8090/api"
W="$(cd "$(dirname "$0")" && pwd -W 2>/dev/null || pwd)/.gk-work"
mkdir -p "$W"
EXAM_AT=$(date -d "+6 hours" +"%Y-%m-%dT%H:%M:%S")
pass=0; fail=0
check() { # check <설명> <상태코드>  — 설명 안에서 명령 치환을 쓰지 않는다($? 가 덮인다)
  if [ "$2" = "0" ]; then echo "  [PASS] $1"; pass=$((pass+1));
  else echo "  [FAIL] $1"; fail=$((fail+1)); fi
}
newcode() { curl -s -X POST "$BASE/sessions" | grep -o '"sessionCode":"[^"]*"' | cut -d'"' -f4; }
psqlq() { docker compose -f "$COMPOSE" exec -T db psql -U postgres -d naeil_study -tAc "$1"; }
COMPOSE="${COMPOSE:-$(dirname "$W")/../docker-compose.yml}"

echo "== A. 자료 없이 시험 범위만으로 생성 =="
CODE=$(newcode); echo "  세션: $CODE"
printf '{"subject":"자료구조","examScope":"3장 스택, 4장 큐, 5장 트리","examAt":"%s","availableStudyMinutes":180}' "$EXAM_AT" > "$W/examA.json"
curl -s -o "$W/exam.json" -w "%{http_code}" -X PUT "$BASE/sessions/$CODE/exam" \
  -H "Content-Type: application/json" --data-binary @"$W/examA.json" > "$W/exam.code"
grep -q "200" "$W/exam.code"; check "A1 시험 정보 저장 200" $?
grep -q '"examScope":"3장 스택, 4장 큐, 5장 트리"' "$W/exam.json"; check "A2 응답이 저장한 examScope 를 그대로 돌려준다" $?

curl -s "$BASE/sessions/$CODE" > "$W/s0.json"
grep -q '"sourceType":null' "$W/s0.json"; check "A3 분석 전 sourceType 은 null" $?
grep -q '"grounded":false' "$W/s0.json"; check "A4 분석 전 grounded 는 false" $?

curl -s -o "$W/an.json" -w "%{http_code}" -X POST "$BASE/sessions/$CODE/analysis" > "$W/an.code"
grep -q "200" "$W/an.code"; check "A5 자료 없이 분석 성공 200" $?
TOPIC_COUNT=$(grep -o '"topicCount":[0-9]*' "$W/an.json" | cut -d: -f2)
[ "${TOPIC_COUNT:-0}" -gt 0 ]; check "A6 Topic 이 1개 이상 만들어졌다 (topicCount=${TOPIC_COUNT:-0})" $?

curl -s "$BASE/sessions/$CODE" > "$W/s1.json"
grep -q '"sourceType":"GENERAL_KNOWLEDGE"' "$W/s1.json"; check "A7 sourceType=GENERAL_KNOWLEDGE" $?
grep -q '"grounded":false' "$W/s1.json"; check "A8 grounded=false" $?

curl -s "$BASE/sessions/$CODE/topics" > "$W/topics.json"
echo "  주제: $(grep -o '"title":"[^"]*"' "$W/topics.json" | cut -d'"' -f4 | tr '\n' '/')"
# 출처 문서는 API 에 노출되지 않는다. 지어낸 문서 ID 가 남지 않았는지 DB 로 확인한다.
SRC=$(psqlq "select coalesce(string_agg(distinct source_document_ids::text, ','), 'none') from topics t join study_sessions s on s.id = t.session_id where s.session_code = '$CODE';" | tr -d '[:space:]')
[ "$SRC" = "[]" ]; check "A9 출처 문서를 지어내지 않았다 (source_document_ids=$SRC)" $?

curl -s -o "$W/cur.json" -w "%{http_code}" -X POST "$BASE/sessions/$CODE/curriculum" > "$W/cur.code"
CUR_CODE=$(cat "$W/cur.code")
grep -qE "20[01]" "$W/cur.code"; check "A10 커리큘럼 생성 성공 ($CUR_CODE)" $?
# 첫 STUDY 단계의 stepId 와 topicId 를 짝지어 꺼낸다. sed 로 n번째 id 를 집는 방식은
# 필드 순서가 바뀌면 조용히 엉뚱한 값을 집는다.
STEP_ID=$(tr ',' '\n' < "$W/cur.json" | grep -A0 '"id":"' | head -2 | tail -1 | cut -d'"' -f4)
STEP_ID=$(docker compose -f "$COMPOSE" exec -T db psql -U postgres -d naeil_study -tAc \
  "select st.id from study_steps st join curriculums c on c.id = st.curriculum_id join study_sessions s on s.id = c.session_id where s.session_code = '$CODE' and st.topic_id is not null order by st.step_order limit 1;" | tr -d '[:space:]')
STEP_TOPIC=$(psqlq "select st.topic_id from study_steps st join curriculums c on c.id = st.curriculum_id join study_sessions s on s.id = c.session_id where s.session_code = '$CODE' and st.topic_id is not null order by st.step_order limit 1;" | tr -d '[:space:]')

curl -s -o /dev/null -X POST "$BASE/sessions/$CODE/steps/$STEP_ID/start"
curl -s -o /dev/null -X POST "$BASE/sessions/$CODE/steps/$STEP_ID/complete"
curl -s -o "$W/quiz.json" -w "%{http_code}" -X POST "$BASE/sessions/$CODE/topics/$STEP_TOPIC/quizzes" > "$W/quiz.code"
QUIZ_CODE=$(cat "$W/quiz.code")
grep -qE "20[01]" "$W/quiz.code"; check "A11 자료 없이도 퀴즈가 만들어진다 ($QUIZ_CODE)" $?
Q=$(grep -o '"question":"' "$W/quiz.json" | wc -l | tr -d ' ')
[ "$Q" -gt 0 ]; check "A12 문제가 실제로 들어 있다 (${Q}문항)" $?

echo
echo "== B. 근거가 아무것도 없으면 거절한다 =="
CODE2=$(newcode)
printf '{"subject":"운영체제","examScope":null,"examAt":"%s","availableStudyMinutes":120}' "$EXAM_AT" > "$W/examB.json"
curl -s -o /dev/null -X PUT "$BASE/sessions/$CODE2/exam" -H "Content-Type: application/json" --data-binary @"$W/examB.json"
curl -s -o "$W/an2.json" -w "%{http_code}" -X POST "$BASE/sessions/$CODE2/analysis" > "$W/an2.code"
echo "  응답: $(cat "$W/an2.code") $(cat "$W/an2.json")"
grep -q "400" "$W/an2.code"; check "B1 시험 범위도 자료도 없으면 400" $?
grep -q 'NO_PARSED_DOCUMENT' "$W/an2.json"; check "B2 code=NO_PARSED_DOCUMENT" $?

echo
echo "== C. 자료가 있으면 자료 기반으로 표시된다 =="
CODE3=$(newcode)
printf '{"subject":"운영체제","examScope":"3장 프로세스","examAt":"%s","availableStudyMinutes":180}' "$EXAM_AT" > "$W/examC.json"
curl -s -o /dev/null -X PUT "$BASE/sessions/$CODE3/exam" -H "Content-Type: application/json" --data-binary @"$W/examC.json"
cat > "$W/os.txt" <<'TXT'
프로세스와 스레드
프로세스는 실행 중인 프로그램이다. 스레드는 프로세스 내부의 실행 단위다.
교착상태는 상호 배제, 점유와 대기, 비선점, 환형 대기 네 조건이 동시에 성립할 때 발생한다.
문맥 교환은 CPU 가 실행 중인 프로세스를 바꿀 때 레지스터 상태를 저장하고 복원하는 과정이다.
TXT
curl -s -o "$W/up.json" -X POST "$BASE/sessions/$CODE3/documents" -F "files=@$W/os.txt;type=text/plain"
grep -q '"originalFileName"' "$W/up.json"; check "C0 자료 업로드 성공" $?
curl -s -o "$W/parse.json" -X POST "$BASE/sessions/$CODE3/documents/parse"
grep -q '"status":"PARSED"' "$W/parse.json"; check "C1 텍스트 추출 성공" $?
curl -s -o "$W/an3.json" -w "%{http_code}" -X POST "$BASE/sessions/$CODE3/analysis" > "$W/an3.code"
AN3=$(cat "$W/an3.code")
grep -q "200" "$W/an3.code"; check "C2 자료 기반 분석 성공 ($AN3)" $?
curl -s "$BASE/sessions/$CODE3" > "$W/s3.json"
grep -q '"sourceType":"USER_MATERIAL"' "$W/s3.json"; check "C3 sourceType=USER_MATERIAL" $?
grep -q '"grounded":true' "$W/s3.json"; check "C4 grounded=true" $?

echo
echo "결과: PASS=$pass FAIL=$fail"
echo "세션코드: A=$CODE B=$CODE2 C=$CODE3"
