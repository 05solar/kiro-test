import { request } from "./client";
import type {
  AnalysisProgressResponse,
  AnalysisResponse,
  ChatHistoryResponse,
  ChatResponse,
  CurriculumResponse,
  DocumentParseResponse,
  DocumentResponse,
  ExamResponse,
  QuizAnswerResponse,
  QuizListResponse,
  QuizResultsResponse,
  SessionResponse,
  StepCompletionResponse,
  StudyStepProgressResponse,
  TopicResponse,
  UpdateExamRequest,
  UpdateStudyContextRequest,
} from "./types";

/**
 * 백엔드 엔드포인트 하나당 함수 하나.
 *
 * <p>경로 문자열을 화면에 흩어 두지 않는다. 백엔드가 경로를 바꾸면 여기만 고친다.
 * 함수 이름은 화면의 동작이 아니라 <b>서버가 하는 일</b>을 따른다.
 */

const s = (code: string) => `/sessions/${encodeURIComponent(code)}`;

/* ── 세션 ─────────────────────────────────────────────── */

export const createSession = () => request<SessionResponse>("/sessions", { method: "POST" });

export const getSession = (code: string) => request<SessionResponse>(s(code));

export const updateExam = (code: string, body: UpdateExamRequest) =>
  request<ExamResponse>(`${s(code)}/exam`, { method: "PUT", json: body });

/* ── 강의자료 ─────────────────────────────────────────── */

/*
 * 목록류 응답은 배열이 아니라 객체로 감싸져 온다 — `{"documents": [...]}`, `{"topics": [...]}`.
 * 여기서 풀어서 배열로 돌려준다. 화면이 감싸는 형식을 알 필요가 없고,
 * 감싼 채로 흘려보내면 화면의 `.map` 이 렌더 중에 터진다 (실제로 그랬다).
 */

/**
 * 여러 파일을 한 번에 올린다. 파트 이름은 `files` 로 고정이다.
 *
 * <p>`Content-Type` 을 직접 넣지 않는다. multipart 경계 문자열은 브라우저가 만든다.
 */
export async function uploadDocuments(code: string, files: File[]): Promise<DocumentResponse[]> {
  const form = new FormData();
  files.forEach((file) => form.append("files", file));
  const res = await request<{ documents: DocumentResponse[] }>(`${s(code)}/documents`, {
    method: "POST",
    body: form,
  });
  return res.documents;
}

export const listDocuments = async (code: string): Promise<DocumentResponse[]> =>
  (await request<{ documents: DocumentResponse[] }>(`${s(code)}/documents`)).documents;

export const deleteDocument = (code: string, documentId: string) =>
  request<void>(`${s(code)}/documents/${documentId}`, { method: "DELETE" });

/**
 * 업로드한 자료에서 텍스트를 뽑는다. 업로드와 분리된 요청이다.
 *
 * <p>항목 형식이 목록 조회와 <b>다르다</b> — `documentId` 를 쓰고 `fileSize` 가 없다.
 * 화면 목록을 갱신할 때는 이 응답이 아니라 {@link listDocuments} 를 다시 부른다.
 */
export const parseDocuments = async (code: string): Promise<DocumentParseResponse[]> =>
  (await request<{ documents: DocumentParseResponse[] }>(`${s(code)}/documents/parse`, {
    method: "POST",
  })).documents;

/* ── 학습 맥락 ────────────────────────────────────────── */

export const updateStudyContext = (code: string, body: UpdateStudyContextRequest) =>
  request<unknown>(`${s(code)}/study-context`, { method: "PUT", json: body });

/* ── 분석 ─────────────────────────────────────────────── */

/** 실제 AI 를 부른다. 자료가 크면 분 단위로 걸릴 수 있다. */
export const runAnalysis = (code: string, signal?: AbortSignal) =>
  request<AnalysisResponse>(`${s(code)}/analysis`, { method: "POST", signal });

/** 분석이 도는 동안 폴링해서 실제 진행도(조각 n/전체)를 받는다. */
export const getAnalysisProgress = (code: string) =>
  request<AnalysisProgressResponse>(`${s(code)}/analysis/progress`);

export const listTopics = async (code: string): Promise<TopicResponse[]> =>
  (await request<{ topics: TopicResponse[] }>(`${s(code)}/topics`)).topics;

/* ── 학습 계획 ────────────────────────────────────────── */

/** 이미 있으면 새로 만들지 않고 기존 계획을 돌려준다. */
export const createCurriculum = (code: string) =>
  request<CurriculumResponse>(`${s(code)}/curriculum`, { method: "POST" });

export const getCurriculum = (code: string) => request<CurriculumResponse>(`${s(code)}/curriculum`);

/* ── 학습 진행 ────────────────────────────────────────── */

export const startStep = (code: string, stepId: string) =>
  request<StudyStepProgressResponse>(`${s(code)}/steps/${stepId}/start`, { method: "POST" });

export const completeStep = (code: string, stepId: string) =>
  request<StepCompletionResponse>(`${s(code)}/steps/${stepId}/complete`, { method: "POST" });

/* ── 퀴즈 ─────────────────────────────────────────────── */

/** 실제 AI 를 부른다. 해당 Topic 의 학습 단계를 완료해야 만들 수 있다. */
export const generateQuizzes = (code: string, topicId: string, signal?: AbortSignal) =>
  request<QuizListResponse>(`${s(code)}/topics/${topicId}/quizzes`, { method: "POST", signal });

export const getQuizzes = (code: string, topicId: string) =>
  request<QuizListResponse>(`${s(code)}/topics/${topicId}/quizzes`);

/**
 * 같은 범위로 <b>새 회차</b>의 문제를 만든다. 실제 AI 를 부른다.
 *
 * <p>"오답 다시 풀기"와 다른 기능이다. 그쪽은 이미 낸 문제를 다시 보는 것이고,
 * 이쪽은 같은 범위에서 다른 문제를 만든다. 기존 문제와 답안 기록은 그대로 남는다.
 *
 * <p>서버가 같은 Topic 의 중복 생성을 막는다. 진행 중이면 409 를 돌려준다.
 */
export const regenerateQuizzes = (code: string, topicId: string, signal?: AbortSignal) =>
  request<QuizListResponse>(`${s(code)}/topics/${topicId}/quizzes/regenerate`, {
    method: "POST",
    signal,
  });

export const answerQuiz = (code: string, quizId: string, selectedIndex: number) =>
  request<QuizAnswerResponse>(`${s(code)}/quizzes/${quizId}/answer`, {
    method: "POST",
    json: { selectedIndex },
  });

export const getQuizResults = (code: string, topicId: string) =>
  request<QuizResultsResponse>(`${s(code)}/topics/${topicId}/quiz-results`);

/* ── 학습 챗봇 ────────────────────────────────────────── */

/**
 * 질문하고 답을 받는다. 실제 AI 를 부른다.
 *
 * <p>지난 대화를 보내지 않는다. 서버가 갖고 있다 — 화면이 보내오게 두면 없던 발화를
 * 지어내 프롬프트에 넣을 수 있다.
 */
export const askStudyChat = (code: string, message: string, signal?: AbortSignal) =>
  request<ChatResponse>(`${s(code)}/chat`, { method: "POST", json: { message }, signal });

/** 이 세션에서 나눈 대화 전체. AI 를 부르지 않는다. */
export const getChatHistory = (code: string) =>
  request<ChatHistoryResponse>(`${s(code)}/chat`);

export { ApiError, NetworkError, toMessage } from "./client";
export type * from "./types";
