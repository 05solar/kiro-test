-- V4 — 대화 순서를 시각이 아니라 번호로 정한다 (2026-08-28)
--
-- 질문과 답변은 한 번에 저장되어 같은 created_at 을 갖는다. 시각이 같으면 정렬에 남는
-- 기준은 무작위로 만들어진 UUID 뿐이라, **답변이 질문 위에 표시되는 일이 실제로 있었다.**
-- 늘 그런 것도 아니고 절반의 확률이라 더 나빴다.
--
-- message_order 는 세션 안에서 1부터 올라간다. Quiz.round 와 같은 방식이다.
--
-- **멱등하게 쓴다.** 이유는 V2 와 같다.

ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS message_order integer;

-- 이미 쌓인 대화에 번호를 매긴다.
--
-- 저장된 시각이 같아 무엇이 먼저였는지 알 수 없는 쌍이 있다. 그래도 한 가지는 확실하다 —
-- **답변은 질문보다 뒤다.** 같은 시각 안에서는 USER 를 앞에 둔다.
-- (role 은 'ASSISTANT' < 'USER' 이므로 내림차순이 USER 먼저다.)
WITH numbered AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY session_id
               ORDER BY created_at ASC, role DESC
           ) AS seq
      FROM chat_messages
)
UPDATE chat_messages
   SET message_order = numbered.seq
  FROM numbered
 WHERE chat_messages.id = numbered.id
   AND chat_messages.message_order IS NULL;

ALTER TABLE chat_messages ALTER COLUMN message_order SET NOT NULL;

-- 조회는 항상 "이 세션의 대화를 순서대로"다. 정렬 기준이 바뀌었으니 인덱스도 바꾼다.
CREATE INDEX IF NOT EXISTS idx_chat_messages_session_id_message_order
    ON chat_messages (session_id, message_order);
DROP INDEX IF EXISTS idx_chat_messages_session_id_created_at;
