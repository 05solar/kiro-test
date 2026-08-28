-- V3 — 학습자료 기반 학습 챗봇 (2026-08-28)
--
-- 대화를 서버가 갖는다. 8자리 코드만으로 다른 기기에서 이어져야 하고,
-- 지난 대화를 클라이언트가 보내오게 두면 없던 발화를 지어내 프롬프트에 넣을 수 있다.
--
-- **멱등하게 쓴다.** 이유는 V2 와 같다.

-- 챗봇 대화 한 줄. 질문과 답변이 각각 한 행이다.
--
-- 대화를 서버가 갖는 이유는 두 가지다. 8자리 코드만으로 다른 기기에서 이어져야 하고,
-- 지난 대화를 클라이언트가 보내오게 두면 없던 발화를 지어내 프롬프트에 넣을 수 있다.
--
-- content 를 TEXT 로 둔다. @Lob 을 쓰면 PostgreSQL 에서 OID 로 저장되어 값이 깨진다.
CREATE TABLE IF NOT EXISTS chat_messages (
    id         uuid                           NOT NULL,
    session_id uuid                           NOT NULL,
    role       character varying(20)          NOT NULL,
    content    text                           NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT chat_messages_pkey PRIMARY KEY (id),
    CONSTRAINT chat_messages_role_check CHECK (role IN ('USER', 'ASSISTANT'))
);

-- 조회는 항상 "이 세션의 최근 대화"다. 세션이 늘어나도 전체를 훑지 않게 한다.
CREATE INDEX IF NOT EXISTS idx_chat_messages_session_id_created_at
    ON chat_messages (session_id, created_at);

-- 세션 삭제는 애플리케이션(SessionPurgeRepository)이 순서대로 처리한다.
-- 이 제약은 그 순서를 어겼을 때 조용히 고아 행이 남지 않게 하는 안전망이다.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_chat_messages_session_id'
    ) THEN
        ALTER TABLE chat_messages
            ADD CONSTRAINT fk_chat_messages_session_id
            FOREIGN KEY (session_id) REFERENCES study_sessions (id);
    END IF;
END
$$;
