#!/usr/bin/env bash
# 실제 Gemini 호출 검증. 한 번만 돌린다.
#
# 부르는 횟수를 세어 둔다. 무료 사용량을 아껴야 하므로 여기 적힌 것 외에는 부르지 않는다.
#   1) 자료 없이 과목명+시험범위로 주제 생성   (분석 1회)
#   2) 그 주제로 퀴즈 생성                     (퀴즈 1회)
#   3) 같은 주제로 새 회차 퀴즈 생성           (퀴즈 1회) — 실제로 다른 문제가 나오는지
#   4) 자료 없는 세션에 챗봇 질문              (챗봇 1회)
#   5) 자료 있는 세션에 챗봇 질문              (챗봇 1회)
# 합계 5회.
set -u
BASE="http://localhost:8090/api"
W="C:/Users/dlthf/AppData/Local/Temp/claude/C--------kiro-project/71380154-1823-452e-91be-018e913e5261/scratchpad/live"
mkdir -p "$W"
EXAM_AT=$(date -d "+8 hours" +"%Y-%m-%dT%H:%M:%S")
GROUNDED_SESSION="${1:-RLUXSC7A}"

echo "=== 1) 자료 없이 주제 생성 (분석 1회) ==="
CODE=$(curl -s -X POST "$BASE/sessions" | grep -o '"sessionCode":"[^"]*"' | cut -d'"' -f4)
echo "세션: $CODE"
printf '{"subject":"운영체제","examScope":"3장 프로세스와 스레드, 4장 CPU 스케줄링, 5장 교착상태","examAt":"%s","availableStudyMinutes":180}' "$EXAM_AT" > "$W/exam.json"
curl -s -o /dev/null -X PUT "$BASE/sessions/$CODE/exam" -H "Content-Type: application/json" --data-binary @"$W/exam.json"

START=$(date +%s)
curl -s -o "$W/analysis.json" -w "%{http_code}" -X POST "$BASE/sessions/$CODE/analysis" > "$W/analysis.code"
echo "응답: $(cat "$W/analysis.code")  ($(($(date +%s)-START))초)"
cat "$W/analysis.json"; echo
curl -s "$BASE/sessions/$CODE/topics" > "$W/topics.json"
echo "--- 생성된 주제 ---"
grep -o '"title":"[^"]*"' "$W/topics.json" | cut -d'"' -f4
echo "--- 첫 주제 요약 ---"
grep -o '"summary":"[^"]*"' "$W/topics.json" | head -1 | cut -d'"' -f4
echo "--- 첫 주제 핵심 개념 ---"
grep -o '"keyPoints":\[[^]]*\]' "$W/topics.json" | head -1

echo
echo "=== 2) 퀴즈 생성 (퀴즈 1회) ==="
curl -s -o "$W/cur.json" -X POST "$BASE/sessions/$CODE/curriculum" > /dev/null
STEP_ID=$(docker compose -f "C:/바탕 화면/kiro-project/docker-compose.yml" exec -T db psql -U postgres -d naeil_study -tAc \
  "select st.id from study_steps st join curriculums c on c.id=st.curriculum_id join study_sessions s on s.id=c.session_id where s.session_code='$CODE' and st.topic_id is not null order by st.step_order limit 1;" | tr -d '[:space:]')
TOPIC_ID=$(docker compose -f "C:/바탕 화면/kiro-project/docker-compose.yml" exec -T db psql -U postgres -d naeil_study -tAc \
  "select st.topic_id from study_steps st join curriculums c on c.id=st.curriculum_id join study_sessions s on s.id=c.session_id where s.session_code='$CODE' and st.topic_id is not null order by st.step_order limit 1;" | tr -d '[:space:]')
curl -s -o /dev/null -X POST "$BASE/sessions/$CODE/steps/$STEP_ID/start"
curl -s -o /dev/null -X POST "$BASE/sessions/$CODE/steps/$STEP_ID/complete"

START=$(date +%s)
curl -s -o "$W/quiz1.json" -w "%{http_code}" -X POST "$BASE/sessions/$CODE/topics/$TOPIC_ID/quizzes" > "$W/quiz1.code"
echo "응답: $(cat "$W/quiz1.code")  ($(($(date +%s)-START))초)"
echo "--- 1회차 문제 ---"
grep -o '"question":"[^"]*"' "$W/quiz1.json" | cut -d'"' -f4

echo
echo "=== 3) 새 회차 퀴즈 (퀴즈 1회) — 실제로 다른 문제가 나오는가 ==="
START=$(date +%s)
curl -s -o "$W/quiz2.json" -w "%{http_code}" -X POST "$BASE/sessions/$CODE/topics/$TOPIC_ID/quizzes/regenerate" > "$W/quiz2.code"
echo "응답: $(cat "$W/quiz2.code")  ($(($(date +%s)-START))초)"
echo "--- 2회차 문제 ---"
grep -o '"question":"[^"]*"' "$W/quiz2.json" | cut -d'"' -f4
echo "--- 겹치는 문제 수 ---"
grep -o '"question":"[^"]*"' "$W/quiz1.json" | sort > "$W/q1.txt"
grep -o '"question":"[^"]*"' "$W/quiz2.json" | sort > "$W/q2.txt"
comm -12 "$W/q1.txt" "$W/q2.txt" | wc -l

echo
echo "=== 4) 자료 없는 세션에 질문 (챗봇 1회) ==="
printf '{"message":"교착상태가 생기는 네 가지 조건을 짧게 정리해줘"}' > "$W/chat1.json"
START=$(date +%s)
curl -s -o "$W/ans1.json" -w "%{http_code}" -X POST "$BASE/sessions/$CODE/chat" \
  -H "Content-Type: application/json" --data-binary @"$W/chat1.json" > "$W/ans1.code"
echo "응답: $(cat "$W/ans1.code")  ($(($(date +%s)-START))초)"
cat "$W/ans1.json"; echo

echo
echo "=== 5) 자료 있는 세션에 질문 (챗봇 1회) ==="
echo "세션: $GROUNDED_SESSION"
printf '{"message":"교착상태 조건이 뭐야?"}' > "$W/chat2.json"
START=$(date +%s)
curl -s -o "$W/ans2.json" -w "%{http_code}" -X POST "$BASE/sessions/$GROUNDED_SESSION/chat" \
  -H "Content-Type: application/json" --data-binary @"$W/chat2.json" > "$W/ans2.code"
echo "응답: $(cat "$W/ans2.code")  ($(($(date +%s)-START))초)"
cat "$W/ans2.json"; echo

echo
echo "=== 끝. 실제 Gemini 호출 5회 ==="
echo "세션코드: 일반지식=$CODE  자료기반=$GROUNDED_SESSION"
