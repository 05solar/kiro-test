-- V5 — 이미 저장된 문제에서 프롬프트 누출을 걷어낸다 (2026-08-28)
--
-- 시스템 프롬프트가 <lecture_context> 로 자료를 구분하는데, 모델이 그 이름을 문제 본문에
-- 그대로 쓴 것이 저장돼 있었다. 학생은 그런 태그를 본 적이 없다.
--
--   "<lecture_context>에 근거할 때, 요구 사항 도출 단계에서 ... 옳은 것은?"
--
-- 앞으로 만들어지는 문제는 프롬프트(QuizPrompts)와 검증기(AiQuizResponseValidator)가
-- 막는다. 이 파일은 그 전에 이미 쌓인 것들을 정리한다.
--
-- **정제 규칙은 AiQuizResponseValidator 와 같아야 한다.** 한쪽만 고치면 화면에서
-- 예전 문제와 새 문제의 문장 모양이 달라진다.
--
-- **멱등하게 쓴다.** 이유는 V2 와 같다. 이미 정리된 행에 다시 돌려도 바뀌는 것이 없다.

-- 1) 문두 출처 머리말을 지운다.
--
--    "자료" 뒤에 조사가 바로 오는 경우만 잡는다. 그래야 "자료구조에서..." 처럼 과목명으로
--    시작하는 정상 문장을 건드리지 않는다.
UPDATE quizzes
   SET question = regexp_replace(
           question,
           '^\s*(다음\s*)?(제공된\s*|주어진\s*|위\s*|해당\s*)?' ||
           '(</?[a-z][a-z0-9_]*>|강의\s*자료|학습\s*자료|수업\s*자료|자료|지문|본문|문서|내용)' ||
           '\s*(에서|에|을|를)?\s*' ||
           '(근거할\s*때|근거하여|근거해서|근거해|바탕으로|기반으로|기초하여|따르면|의하면)' ||
           '\s*(할\s*때|하면|보면|살펴보면)?\s*[,:]?\s*',
           '')
 WHERE question ~ '^\s*(다음\s*)?(제공된\s*|주어진\s*|위\s*|해당\s*)?(</?[a-z][a-z0-9_]*>|강의\s*자료|학습\s*자료|수업\s*자료|자료|지문|본문|문서|내용)\s*(에서|에|을|를)?\s*(근거할\s*때|근거하여|근거해서|근거해|바탕으로|기반으로|기초하여|따르면|의하면)';

UPDATE quizzes
   SET explanation = regexp_replace(
           explanation,
           '^\s*(다음\s*)?(제공된\s*|주어진\s*|위\s*|해당\s*)?' ||
           '(</?[a-z][a-z0-9_]*>|강의\s*자료|학습\s*자료|수업\s*자료|자료|지문|본문|문서|내용)' ||
           '\s*(에서|에|을|를)?\s*' ||
           '(근거할\s*때|근거하여|근거해서|근거해|바탕으로|기반으로|기초하여|따르면|의하면)' ||
           '\s*(할\s*때|하면|보면|살펴보면)?\s*[,:]?\s*',
           '')
 WHERE explanation ~ '^\s*(다음\s*)?(제공된\s*|주어진\s*|위\s*|해당\s*)?(</?[a-z][a-z0-9_]*>|강의\s*자료|학습\s*자료|수업\s*자료|자료|지문|본문|문서|내용)\s*(에서|에|을|를)?\s*(근거할\s*때|근거하여|근거해서|근거해|바탕으로|기반으로|기초하여|따르면|의하면)';

-- 2) 문장 중간에 남은 태그는 지우지 않고 "자료"로 바꾼다.
--
--    태그가 명사 자리를 차지하고 있어 지우면 문장이 깨진다.
--      "...협력하는 대상으로 <lecture_context>에서 언급된 것은?"
--      지우면 → "...대상으로 에서 언급된 것은?"    (깨진다)
--      바꾸면 → "...대상으로 자료에서 언급된 것은?" (읽힌다)
UPDATE quizzes
   SET question = regexp_replace(question, '</?[a-z][a-z0-9_]*>', '자료', 'g')
 WHERE question ~ '</?[a-z][a-z0-9_]*>';

UPDATE quizzes
   SET explanation = regexp_replace(explanation, '</?[a-z][a-z0-9_]*>', '자료', 'g')
 WHERE explanation ~ '</?[a-z][a-z0-9_]*>';

-- 보기는 jsonb 배열이라 요소마다 바꿔 다시 담는다.
UPDATE quizzes
   SET options = (
           SELECT jsonb_agg(regexp_replace(value, '</?[a-z][a-z0-9_]*>', '자료', 'g') ORDER BY ordinality)
             FROM jsonb_array_elements_text(options) WITH ORDINALITY AS t(value, ordinality)
       )
 WHERE options::text ~ '</?[a-z][a-z0-9_]*>';

-- 3) 정제 후 남은 이중 공백을 정리한다.
UPDATE quizzes
   SET question = btrim(regexp_replace(question, '\s{2,}', ' ', 'g')),
       explanation = btrim(regexp_replace(explanation, '\s{2,}', ' ', 'g'))
 WHERE question ~ '\s{2,}' OR explanation ~ '\s{2,}'
    OR question ~ '^\s' OR question ~ '\s$';
