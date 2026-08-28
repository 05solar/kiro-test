/**
 * 백엔드 응답 타입.
 *
 * <p>백엔드 DTO 를 그대로 옮긴 것이다. 화면에서 쓰기 좋은 형태로 바꾸는 일은
 * `adapt.ts` 가 한다. 이 파일에는 변환 로직을 넣지 않는다 — 서버 계약이 바뀌었을 때
 * 어디를 고쳐야 하는지가 분명해야 하기 때문이다.
 *
 * 명세: `docs/api/` (저장소 루트 기준)
 */

export type SessionStatus =
  | "CREATED"
  | "UPLOADING"
  | "ANALYZING"
  | "ANALYSIS_FAILED"
  | "READY"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "EXPIRED";

export type TopicImportance = "VERY_HIGH" | "HIGH" | "MEDIUM" | "LOW";
export type DocumentStatus = "UPLOADED" | "PARSING" | "PARSED" | "PARSE_FAILED";
export type StudyStepStatus = "PENDING" | "IN_PROGRESS" | "COMPLETED" | "SKIPPED";
export type StudyStepType = "STUDY" | "REVIEW";
export type CurriculumStatus = "CREATED" | "IN_PROGRESS" | "COMPLETED";
export type QuizDifficulty = "EASY" | "MEDIUM" | "HARD";

export type PriorityReason =
  | "CORE_TOPIC"
  | "PROFESSOR_EMPHASIS"
  | "PAST_EXAM"
  | "WEAK_AREA"
  | "MUST_STUDY";

export type StudySourceType = "USER_MATERIAL" | "GENERAL_KNOWLEDGE";

export interface SessionResponse {
  sessionCode: string;
  subject: string | null;
  /** 시험 범위. 자료가 없을 때 학습 내용을 만드는 유일한 근거가 된다. */
  examScope: string | null;
  /** 실제 강의자료에 근거했는지. 분석 전에는 false 다. */
  grounded: boolean;
  /** 무엇에 근거했는지. 분석 전에는 null 이다. */
  sourceType: StudySourceType | null;
  examAt: string | null;
  availableStudyMinutes: number | null;
  remainingStudyMinutes: number | null;
  status: SessionStatus;
  currentStepOrder?: number | null;
}

export interface UpdateExamRequest {
  subject: string;
  /** 시험 범위. 선택 입력이지만, 자료를 올리지 않을 거라면 반드시 있어야 한다. */
  examScope: string | null;
  /** `2026-08-29T09:00:00` 형태. 시간대 표기를 붙이지 않는다. */
  examAt: string;
  availableStudyMinutes: number;
}

/** `PUT /exam` 응답. 세션 전체가 아니라 저장한 시험 정보만 돌려준다. */
export interface ExamResponse {
  sessionCode: string;
  subject: string;
  examScope: string | null;
  examAt: string;
  availableStudyMinutes: number;
  remainingStudyMinutes: number;
  status: SessionStatus;
}

export interface DocumentResponse {
  id: string;
  originalFileName: string;
  fileType: "PDF" | "DOCX" | "TXT";
  fileSize: number;
  status: DocumentStatus;
  characterCount: number | null;
  parsedAt: string | null;
  createdAt: string;
}

/**
 * 텍스트 추출 응답의 항목. {@link DocumentResponse} 와 형식이 다르다 —
 * `documentId` 를 쓰고 `fileSize`/`createdAt` 이 없다.
 */
export interface DocumentParseResponse {
  documentId: string;
  originalFileName: string;
  status: DocumentStatus;
  characterCount: number | null;
  parsedAt: string | null;
}

export interface UpdateStudyContextRequest {
  professorEmphasis: string | null;
  pastExamInfo: string | null;
  weakAreas: string | null;
  mustStudyAreas: string | null;
}

export interface AnalysisResponse {
  sessionCode: string;
  status: SessionStatus;
  topicCount: number;
}

/** 분석 진행도. 서버가 실제로 처리한 조각 수를 준다 — 화면 타이머로 흉내내지 않는다. */
export interface AnalysisProgressResponse {
  phase: "NONE" | "PREPARING" | "ANALYZING" | "MERGING" | "SAVING" | "DONE" | "FAILED";
  completedChunks: number;
  totalChunks: number;
  /** 0~100. 조각 분석 구간에 실제 비율이 펴져 있다. */
  percent: number;
}

export interface TopicResponse {
  id: string;
  title: string;
  summary: string;
  keyPoints: string[];
  importance: TopicImportance;
  estimatedStudyMinutes: number;
  professorEmphasisMatched: boolean;
  pastExamMatched: boolean;
  weakAreaMatched: boolean;
  mustStudyMatched: boolean;
  topicOrder: number;
}

export interface CurriculumProgressResponse {
  completedSteps: number;
  totalSteps: number;
  percentage: number;
  skippedSteps?: number;
}

export interface StudyStepResponse {
  id: string;
  order: number;
  type: StudyStepType;
  topicId: string | null;
  title: string;
  importance: TopicImportance | null;
  originalEstimatedMinutes: number;
  allocatedMinutes: number;
  actualStudyMinutes: number | null;
  mandatory: boolean;
  priorityReasons: PriorityReason[];
  status: StudyStepStatus;
  startedAt: string | null;
  completedAt: string | null;
}

export interface CurriculumResponse {
  curriculumId: string;
  initialRemainingMinutes: number;
  totalAllocatedMinutes: number;
  status: CurriculumStatus;
  progress: CurriculumProgressResponse;
  steps: StudyStepResponse[];
}

export interface StudyStepProgressResponse {
  stepId: string;
  stepOrder: number;
  type: StudyStepType;
  title: string;
  status: StudyStepStatus;
  allocatedMinutes: number;
  actualStudyMinutes: number | null;
  startedAt: string | null;
  completedAt: string | null;
}

export interface StepCompletionResponse {
  completedStep: StudyStepProgressResponse;
  time?: { remainingStudyMinutes: number };
  reallocation?: {
    changed: boolean;
    steps: {
      stepId: string;
      previousAllocatedMinutes: number;
      allocatedMinutes: number;
      status: StudyStepStatus;
    }[];
  };
  nextStep: StudyStepProgressResponse | null;
  curriculumCompleted: boolean;
}

/** 퀴즈 조회 응답에는 정답이 없다. 채점은 서버가 한다. */
export interface QuizResponse {
  id: string;
  order: number;
  question: string;
  options: string[];
  difficulty: QuizDifficulty;
}

export interface QuizListResponse {
  topicId: string;
  topicTitle: string;
  quizzes: QuizResponse[];
}

export interface QuizAnswerResponse {
  quizId: string;
  selectedIndex: number;
  correctIndex: number;
  correct: boolean;
  explanation: string;
  answeredAt: string;
}

/**
 * 채점 기록 한 건.
 *
 * <p><b>문제 본문도, 정답도 들어 있지 않다.</b> 서버는 답안 기록만 돌려준다.
 * 문제와 보기는 퀴즈 조회로 따로 받아 {@code quizId} 로 이어 붙여야 한다.
 *
 * <p>정답 번호는 답안을 제출한 그 순간의 응답({@link QuizAnswerResponse})에만 있다.
 * 결과 화면을 새로고침하면 다시 받을 수 없다.
 */
export interface AnsweredQuizResponse {
  quizId: string;
  selectedIndex: number;
  correct: boolean;
  answeredAt: string;
}

export interface QuizResultsResponse {
  topicId: string;
  totalQuestions: number;
  answeredQuestions: number;
  correctAnswers: number;
  scorePercentage: number;
  completed: boolean;
  results: AnsweredQuizResponse[];
}

export type ChatRole = "USER" | "ASSISTANT";

export interface ChatRequest {
  /** 질문. 1000자 이하. 지난 대화는 보내지 않는다 — 서버가 갖고 있다. */
  message: string;
}

export interface ChatResponse {
  answer: string;
  /** 이 세션이 강의자료에 근거하는지. */
  grounded: boolean;
  /**
   * 이 답변이 실제로 자료에서 확인된 내용인지.
   *
   * <p>자료가 있는 세션이라도 질문이 자료 밖이면 false 다. 화면은 이 값으로
   * "자료에 없어 일반 지식으로 답했다"를 알린다.
   */
  answeredFromMaterial: boolean;
  answeredAt: string;
}

export interface ChatMessageResponse {
  role: ChatRole;
  content: string;
  createdAt: string;
}

export interface ChatHistoryResponse {
  grounded: boolean;
  messages: ChatMessageResponse[];
}

/** 백엔드의 공통 에러 응답. */
export interface ErrorResponse {
  code: string;
  message: string;
}

/**
 * 풀이 내역의 문제 하나.
 *
 * <p>정답({@code correctIndex})과 해설은 <b>답한 문제에만</b> 들어 있다.
 * 안 푼 문제는 둘 다 null 이다 — 내역 화면이 정답 미리보기 통로가 되지 않도록
 * 서버가 빼고 내려준다.
 */
export interface QuizReviewItemResponse {
  quizId: string;
  quizOrder: number;
  question: string;
  options: string[];
  difficulty: QuizDifficulty;
  answered: boolean;
  selectedIndex: number | null;
  correct: boolean;
  correctIndex: number | null;
  explanation: string | null;
  answeredAt: string | null;
}

/** 한 STEP(Topic)의 풀이 내역. */
export interface QuizReviewResponse {
  topicId: string;
  topicTitle: string;
  /** 마지막 회차. "새로운 퀴즈"를 만들면 올라간다. */
  round: number;
  totalQuestions: number;
  answeredQuestions: number;
  wrongQuestions: number;
  items: QuizReviewItemResponse[];
}

/** 정리 화면의 STEP 하나. 요약과 그 STEP 의 문제들을 함께 담는다. */
export interface StepReviewResponse {
  stepId: string;
  stepOrder: number;
  title: string;
  type: StudyStepType;
  status: StudyStepStatus;
  allocatedMinutes: number;
  actualStudyMinutes: number | null;
  /** 휴식·복습처럼 Topic 이 없는 STEP 이면 null 이다. */
  topicId: string | null;
  topicTitle: string | null;
  summary: string | null;
  keyPoints: string[];
  importance: TopicImportance | null;
  round: number;
  totalQuestions: number;
  answeredQuestions: number;
  wrongQuestions: number;
  quizzes: QuizReviewItemResponse[];
}

/**
 * 세션 전체 정리.
 *
 * <p>스텝별 요약 / 푼 문제 전체 / 틀린 문제만 — 세 화면이 이 응답 하나를 나눠 쓴다.
 * 틀린 문제만 보는 화면도 따로 부르지 않고 correct 가 false 인 것을 골라 쓴다.
 */
export interface SessionReviewResponse {
  sessionCode: string;
  subject: string | null;
  examScope: string | null;
  examAt: string | null;
  /** 모든 STEP 을 마쳤는가. 건너뛴 STEP 은 남은 것으로 세지 않는다. */
  completed: boolean;
  totalSteps: number;
  completedSteps: number;
  totalQuestions: number;
  answeredQuestions: number;
  correctAnswers: number;
  wrongAnswers: number;
  /** 푼 문제 기준 정답률. 안 푼 문제는 분모에서 뺀다. */
  scorePercentage: number;
  steps: StepReviewResponse[];
}
