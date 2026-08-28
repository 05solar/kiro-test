-- 자료 미업로드 시 일반 지식 기반 생성 (2026-08-28)
--
-- 운영 프로파일은 ddl-auto=validate 다. 엔티티에 컬럼을 더해도 스키마는 자동으로 바뀌지 않고,
-- 적용하지 않으면 애플리케이션이 기동 중 Schema-validation 으로 죽는다.
-- 새 버전을 배포하기 **전에** 먼저 적용한다.
--
--   psql -h <endpoint> -U <user> -d naeil_study -f docs/migrations/001-general-knowledge.sql
--
-- 새 DB 는 docs/schema.sql 에 이미 반영되어 있으므로 이 파일을 적용할 필요가 없다.

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
