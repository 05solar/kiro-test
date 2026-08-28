-- V2 — 자료 미업로드 시 일반 지식 기반 생성 (2026-08-28)
--
-- 강의자료를 올리지 않아도 과목명과 시험 범위만으로 학습 내용을 만든다.
-- 그러려면 시험 범위를 저장할 자리(exam_scope)와, 무엇에 근거해 만들었는지를
-- 남길 자리(source_type)가 필요하다.
--
-- **멱등하게 쓴다.** 이 파일은 이미 컬럼이 있는 DB 에도 실행될 수 있다 —
-- Flyway 를 도입하기 전에 손으로 적용해 둔 DB 는 baseline(V1)으로 표시된 뒤
-- V2 부터 다시 실행되기 때문이다.

-- 시험 범위. 강의자료를 올리지 않은 경우 학습 내용을 만드는 유일한 근거가 된다.
-- @Lob 을 쓰지 않고 TEXT 로 둔다 — PostgreSQL 에서 @Lob String 은 OID 로 저장되어 값이 깨진다.
ALTER TABLE study_sessions ADD COLUMN IF NOT EXISTS exam_scope text;

-- 학습 내용을 무엇에 근거해 만들었는지. 분석을 시작할 때 정해진다.
-- USER_MATERIAL / GENERAL_KNOWLEDGE. 분석 전에는 NULL 이다.
ALTER TABLE study_sessions ADD COLUMN IF NOT EXISTS source_type character varying(30);

-- 기존 세션은 전부 강의자료로 만들었다. 분석까지 끝난 세션에만 표시를 남긴다.
-- (아직 분석하지 않은 세션은 NULL 로 두어야 화면이 "근거 없음"으로 올바르게 그린다.)
UPDATE study_sessions
   SET source_type = 'USER_MATERIAL'
 WHERE source_type IS NULL
   AND status IN ('READY', 'IN_PROGRESS', 'COMPLETED');
