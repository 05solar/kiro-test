#!/usr/bin/env bash
# 2단계(학습 챗봇) 검증. LLM_MODE=mock 이므로 실제 AI 를 부르지 않는다.
#
# 함정 두 가지는 gk-verify.sh 와 같다.
#  - 한글 본문은 UTF-8 파일로 써서 --data-binary 로 보낸다.
#  - 작업 디렉터리는 Windows 경로로 둔다(mingw curl).
set -u
BASE="http://localhost:8090/api"
W="$(cd "$(dirname "$0")" && pwd -W 2>/dev/null || pwd)/.gk-work"
mkdir -p "$W"
COMPOSE="${COMPOSE:-$(dirname "$W")/../docker-compose.yml}"
EXAM_AT=$(date -d "+6 hours" +"%Y-%m-%dT%H:%M:%S")
pass=0; fail=0
check() { # check <설명> <상태코드> — 설명 안에서 명령 치환을 쓰지 않는다($? 가 덮인다)
  if [ "$2" = "0" ]; then echo "  [PASS] $1"; pass=$((pass+1));
  else echo "  [FAIL] $1"; fail=$((fail+1)); fi
}
newcode() { curl -s -X POST "$BASE/sessions" | grep -o '"sessionCode":"[^"]*"' | cut -d'"' -f4; }
ask() { # ask <code> <파일명>
  curl -s -o "$W/ans.json" -w "%{http_code}" -X POST "$BASE/sessions/$1/chat" \
    -H "Content-Type: application/json" --data-binary @"$W/$2" > "$W/ans.code"
}

echo "== A. 자료 기반 세션 =="
CODE=$(newcode); echo "  세션: $CODE"
printf '{"subject":"운영체제","examScope":"3장 프로세스","examAt":"%s","availableStudyMinutes":180}' "$EXAM_AT" > "$W/exam.json"
curl -s -o /dev/null -X PUT "$BASE/sessions/$CODE/exam" -H "Content-Type: application/json" --data-binary @"$W/exam.json"

cat > "$W/os.txt" <<'TXT'
프로세스와 스레드
프로세스는 실행 중인 프로그램이다. 스레드는 프로세스 내부의 실행 단위다.
교착상태는 상호 배제, 점유와 대기, 비선점, 환형 대기 네 조건이 동시에 성립할 때 발생한다.
문맥 교환은 CPU 가 실행 중인 프로세스를 바꿀 때 레지스터 상태를 저장하고 복원하는 과정이다.
TXT
curl -s -o /dev/null -X POST "$BASE/sessions/$CODE/documents" -F "files=@$W/os.txt;type=text/plain"
curl -s -o /dev/null -X POST "$BASE/sessions/$CODE/documents/parse"

printf '{"message":"교착상태 조건이 뭐야?"}' > "$W/q1.json"
ask "$CODE" q1.json
grep -q "409" "$W/ans.code"; check "A1 분석 전에는 409 — 답할 근거가 없다" $?
grep -q "CHAT_NOT_READY" "$W/ans.json"; check "A2 code=CHAT_NOT_READY" $?

curl -s -o /dev/null -X POST "$BASE/sessions/$CODE/analysis"

ask "$CODE" q1.json
ANS_CODE=$(cat "$W/ans.code")
grep -q "200" "$W/ans.code"; check "A3 분석 후에는 답한다 ($ANS_CODE)" $?
grep -q '"grounded":true' "$W/ans.json"; check "A4 자료 기반으로 표시된다" $?
grep -q '"answeredFromMaterial":true' "$W/ans.json"; check "A5 자료에서 근거를 찾았다고 표시한다" $?
echo "  답변: $(grep -o '"answer":"[^"]*"' "$W/ans.json" | head -c 160)"

printf '{"message":"블록체인 합의 알고리즘 설명해줘"}' > "$W/q2.json"
ask "$CODE" q2.json
grep -q '"answeredFromMaterial":false' "$W/ans.json"; check "A6 자료 밖 질문은 자료 기반이 아니라고 알린다" $?

curl -s "$BASE/sessions/$CODE/chat" > "$W/hist.json"
COUNT=$(grep -o '"role":"' "$W/hist.json" | wc -l | tr -d ' ')
[ "$COUNT" = "4" ]; check "A7 대화가 그대로 저장돼 다시 읽힌다 (${COUNT}줄)" $?
grep -q '"role":"USER"' "$W/hist.json"; check "A8 질문이 남아 있다" $?
grep -q '"role":"ASSISTANT"' "$W/hist.json"; check "A9 답변이 남아 있다" $?
grep -q '"grounded":true' "$W/hist.json"; check "A10 조회 응답에도 근거 표시가 있다" $?

echo
echo "== B. 자료가 없는 세션 =="
CODE2=$(newcode)
printf '{"subject":"자료구조","examScope":"3장 스택, 4장 큐","examAt":"%s","availableStudyMinutes":180}' "$EXAM_AT" > "$W/exam2.json"
curl -s -o /dev/null -X PUT "$BASE/sessions/$CODE2/exam" -H "Content-Type: application/json" --data-binary @"$W/exam2.json"
curl -s -o /dev/null -X POST "$BASE/sessions/$CODE2/analysis"

printf '{"message":"스택이 뭐야?"}' > "$W/q3.json"
ask "$CODE2" q3.json
B_CODE=$(cat "$W/ans.code")
grep -q "200" "$W/ans.code"; check "B1 자료가 없어도 답한다 ($B_CODE)" $?
grep -q '"grounded":false' "$W/ans.json"; check "B2 자료 기반이라고 하지 않는다" $?
grep -q '"answeredFromMaterial":false' "$W/ans.json"; check "B3 자료에서 찾았다고 하지 않는다" $?

echo
echo "== C. 입력과 접근 제한 =="
printf '{"message":"   "}' > "$W/blank.json"
ask "$CODE2" blank.json
grep -q "400" "$W/ans.code"; check "C1 빈 질문은 400" $?

# 1000자를 넘기는 질문
printf '{"message":"' > "$W/long.json"
for i in $(seq 1 101); do printf '0123456789' >> "$W/long.json"; done
printf '"}' >> "$W/long.json"
ask "$CODE2" long.json
grep -q "400" "$W/ans.code"; check "C2 1000자를 넘는 질문은 400" $?

curl -s -o "$W/other.json" -w "%{http_code}" "$BASE/sessions/ZZZZZZZZ/chat" > "$W/other.code"
grep -q "404" "$W/other.code"; check "C3 없는 세션의 대화는 404" $?
grep -q "SESSION_NOT_FOUND" "$W/other.json"; check "C4 code=SESSION_NOT_FOUND" $?

curl -s "$BASE/sessions/$CODE2/chat" > "$W/hist2.json"
grep -q '스택이 뭐야?' "$W/hist2.json"; check "C5 내 대화는 내 세션에만 있다" $?
grep -q '교착상태' "$W/hist2.json" && check "C6 남의 대화가 섞이지 않는다" 1 || check "C6 남의 대화가 섞이지 않는다" 0

echo
echo "결과: PASS=$pass FAIL=$fail"
echo "세션코드: A=$CODE B=$CODE2"
