-- V1 — 최초 스키마 (1단계 이전 상태)
--
-- 이 파일은 개발 DB 에서 pg_dump --schema-only 로 뽑은 것을 옮겼다.
-- psql 전용 지시어(\restrict, SET, search_path 설정)는 JDBC 로 실행할 수 없어 걷어냈다.
--
-- 이미 테이블이 있는 DB 에는 적용되지 않는다. Flyway 가 baseline 으로 건너뛴다
-- (spring.flyway.baseline-version=1). 그래서 이 파일은 멱등할 필요가 없다.
--
-- **이 파일을 고치지 않는다.** 이미 적용된 마이그레이션의 내용을 바꾸면 체크섬이
-- 어긋나 다음 기동이 실패한다. 스키마를 바꾸려면 새 번호의 파일을 추가한다.

CREATE TABLE public.curriculums (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    initial_remaining_minutes integer NOT NULL,
    status character varying(20) NOT NULL,
    total_allocated_minutes integer NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    session_id uuid NOT NULL,
    CONSTRAINT curriculums_status_check CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying])::text[])))
);


--
-- Name: documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documents (
    id uuid NOT NULL,
    character_count integer,
    created_at timestamp(6) without time zone NOT NULL,
    extracted_text text,
    file_size bigint NOT NULL,
    file_type character varying(10) NOT NULL,
    original_file_name character varying(255) NOT NULL,
    parse_error_message character varying(500),
    parsed_at timestamp(6) without time zone,
    status character varying(20) NOT NULL,
    storage_path character varying(500) NOT NULL,
    stored_file_name character varying(100) NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    session_id uuid NOT NULL,
    CONSTRAINT documents_file_type_check CHECK (((file_type)::text = ANY ((ARRAY['PDF'::character varying, 'DOCX'::character varying, 'TXT'::character varying])::text[]))),
    CONSTRAINT documents_status_check CHECK (((status)::text = ANY ((ARRAY['UPLOADED'::character varying, 'PARSING'::character varying, 'PARSED'::character varying, 'PARSE_FAILED'::character varying])::text[])))
);


--
-- Name: quiz_results; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_results (
    id uuid NOT NULL,
    answered_at timestamp(6) without time zone NOT NULL,
    is_correct boolean NOT NULL,
    selected_index integer NOT NULL,
    quiz_id uuid NOT NULL,
    session_id uuid NOT NULL
);


--
-- Name: quizzes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quizzes (
    id uuid NOT NULL,
    correct_index integer NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    difficulty character varying(20) NOT NULL,
    explanation text NOT NULL,
    options jsonb NOT NULL,
    question text NOT NULL,
    quiz_order integer NOT NULL,
    round integer NOT NULL,
    source_document_ids jsonb NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    topic_id uuid NOT NULL,
    CONSTRAINT quizzes_difficulty_check CHECK (((difficulty)::text = ANY ((ARRAY['EASY'::character varying, 'MEDIUM'::character varying, 'HARD'::character varying])::text[])))
);


--
-- Name: study_contexts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.study_contexts (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    must_study_areas text,
    past_exam_info text,
    professor_emphasis text,
    updated_at timestamp(6) without time zone NOT NULL,
    weak_areas text,
    session_id uuid NOT NULL
);


--
-- Name: study_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.study_sessions (
    id uuid NOT NULL,
    available_study_minutes integer,
    created_at timestamp(6) without time zone NOT NULL,
    current_step_order integer,
    exam_at timestamp(6) without time zone,
    expires_at timestamp(6) without time zone NOT NULL,
    last_accessed_at timestamp(6) without time zone NOT NULL,
    remaining_study_minutes integer,
    session_code character varying(8) NOT NULL,
    status character varying(20) NOT NULL,
    subject character varying(255),
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT study_sessions_status_check CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'UPLOADING'::character varying, 'ANALYZING'::character varying, 'ANALYSIS_FAILED'::character varying, 'READY'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: study_steps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.study_steps (
    id uuid NOT NULL,
    actual_study_minutes integer,
    allocated_minutes integer NOT NULL,
    completed_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    is_mandatory boolean NOT NULL,
    original_estimated_minutes integer NOT NULL,
    priority_reasons jsonb NOT NULL,
    skip_reason character varying(30),
    started_at timestamp(6) without time zone,
    status character varying(20) NOT NULL,
    step_order integer NOT NULL,
    title character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    curriculum_id uuid NOT NULL,
    topic_id uuid,
    CONSTRAINT study_steps_skip_reason_check CHECK (((skip_reason)::text = 'TIME_CONSTRAINT'::text)),
    CONSTRAINT study_steps_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'SKIPPED'::character varying])::text[]))),
    CONSTRAINT study_steps_type_check CHECK (((type)::text = ANY ((ARRAY['STUDY'::character varying, 'REVIEW'::character varying])::text[])))
);


--
-- Name: topics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.topics (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    estimated_study_minutes integer NOT NULL,
    importance character varying(20) NOT NULL,
    key_points jsonb NOT NULL,
    must_study_matched boolean NOT NULL,
    past_exam_matched boolean NOT NULL,
    professor_emphasis_matched boolean NOT NULL,
    source_document_ids jsonb NOT NULL,
    summary text NOT NULL,
    title character varying(200) NOT NULL,
    topic_order integer NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    weak_area_matched boolean NOT NULL,
    session_id uuid NOT NULL,
    CONSTRAINT topics_importance_check CHECK (((importance)::text = ANY ((ARRAY['VERY_HIGH'::character varying, 'HIGH'::character varying, 'MEDIUM'::character varying, 'LOW'::character varying])::text[])))
);


--
-- Name: wrong_answer_summaries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wrong_answer_summaries (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    generated_at timestamp(6) without time zone NOT NULL,
    overall_summary text NOT NULL,
    source_latest_answered_at timestamp(6) without time zone NOT NULL,
    topic_reviews jsonb NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    wrong_answer_count integer NOT NULL,
    session_id uuid NOT NULL
);


--
-- Name: curriculums curriculums_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculums
    ADD CONSTRAINT curriculums_pkey PRIMARY KEY (id);


--
-- Name: documents documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_pkey PRIMARY KEY (id);


--
-- Name: quiz_results quiz_results_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_results
    ADD CONSTRAINT quiz_results_pkey PRIMARY KEY (id);


--
-- Name: quizzes quizzes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quizzes
    ADD CONSTRAINT quizzes_pkey PRIMARY KEY (id);


--
-- Name: study_contexts study_contexts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_contexts
    ADD CONSTRAINT study_contexts_pkey PRIMARY KEY (id);


--
-- Name: study_sessions study_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_sessions
    ADD CONSTRAINT study_sessions_pkey PRIMARY KEY (id);


--
-- Name: study_steps study_steps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_steps
    ADD CONSTRAINT study_steps_pkey PRIMARY KEY (id);


--
-- Name: topics topics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.topics
    ADD CONSTRAINT topics_pkey PRIMARY KEY (id);


--
-- Name: curriculums uk_curriculums_session_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculums
    ADD CONSTRAINT uk_curriculums_session_id UNIQUE (session_id);


--
-- Name: quiz_results uk_quiz_results_session_id_quiz_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_results
    ADD CONSTRAINT uk_quiz_results_session_id_quiz_id UNIQUE (session_id, quiz_id);


--
-- Name: quizzes uk_quizzes_topic_id_round_quiz_order; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quizzes
    ADD CONSTRAINT uk_quizzes_topic_id_round_quiz_order UNIQUE (topic_id, round, quiz_order);


--
-- Name: study_contexts uk_study_contexts_session_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_contexts
    ADD CONSTRAINT uk_study_contexts_session_id UNIQUE (session_id);


--
-- Name: study_sessions uk_study_sessions_session_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_sessions
    ADD CONSTRAINT uk_study_sessions_session_code UNIQUE (session_code);


--
-- Name: wrong_answer_summaries uk_wrong_answer_summaries_session_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wrong_answer_summaries
    ADD CONSTRAINT uk_wrong_answer_summaries_session_id UNIQUE (session_id);


--
-- Name: wrong_answer_summaries wrong_answer_summaries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wrong_answer_summaries
    ADD CONSTRAINT wrong_answer_summaries_pkey PRIMARY KEY (id);


--
-- Name: study_steps fk5li8b4v8980amaxooq97iw62v; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_steps
    ADD CONSTRAINT fk5li8b4v8980amaxooq97iw62v FOREIGN KEY (topic_id) REFERENCES public.topics(id);


--
-- Name: study_steps fk7vk6xg06wufe8lw7i4ik237fj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_steps
    ADD CONSTRAINT fk7vk6xg06wufe8lw7i4ik237fj FOREIGN KEY (curriculum_id) REFERENCES public.curriculums(id);


--
-- Name: documents fkal1v59ijkepint4q3ab3ok6it; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT fkal1v59ijkepint4q3ab3ok6it FOREIGN KEY (session_id) REFERENCES public.study_sessions(id);


--
-- Name: quizzes fkfr4ylj9b7jdlul8kdhqxa5il9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quizzes
    ADD CONSTRAINT fkfr4ylj9b7jdlul8kdhqxa5il9 FOREIGN KEY (topic_id) REFERENCES public.topics(id);


--
-- Name: topics fkhp6f0dib8w5g3wiv34pqml5n1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.topics
    ADD CONSTRAINT fkhp6f0dib8w5g3wiv34pqml5n1 FOREIGN KEY (session_id) REFERENCES public.study_sessions(id);


--
-- Name: quiz_results fkmmvfwhutqbn5u6n25e4405pw9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_results
    ADD CONSTRAINT fkmmvfwhutqbn5u6n25e4405pw9 FOREIGN KEY (quiz_id) REFERENCES public.quizzes(id);


--
-- Name: wrong_answer_summaries fkmpckpveput6anxfjsgfw1y0nn; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wrong_answer_summaries
    ADD CONSTRAINT fkmpckpveput6anxfjsgfw1y0nn FOREIGN KEY (session_id) REFERENCES public.study_sessions(id);


--
-- Name: curriculums fkmqw1nmh4bp5g2r9blchvasnfr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculums
    ADD CONSTRAINT fkmqw1nmh4bp5g2r9blchvasnfr FOREIGN KEY (session_id) REFERENCES public.study_sessions(id);


--
-- Name: study_contexts fkqulmsep64p4ykum34cvbgql0e; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_contexts
    ADD CONSTRAINT fkqulmsep64p4ykum34cvbgql0e FOREIGN KEY (session_id) REFERENCES public.study_sessions(id);


--
-- Name: quiz_results fkr0d4t2jg4rds7lfd9d55p9mnq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_results
    ADD CONSTRAINT fkr0d4t2jg4rds7lfd9d55p9mnq FOREIGN KEY (session_id) REFERENCES public.study_sessions(id);


--
-- PostgreSQL database dump complete
--


