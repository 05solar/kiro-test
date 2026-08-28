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

export interface SessionResponse {
  sessionCode: string;
  subject: string | null;
  examAt: string | null;
  availableStudyMinutes: number | null;
  remainingStudyMinutes: number | null;
  status: SessionStatus;
  currentStepOrder?: number | null;
}

export interface UpdateExamRequest {
  subject: string;
  /** `2026-08-29T09:00:00` 형태. 시간대 표기를 붙이지 않는다. */
  examAt: string;
  availableStudyMinutes: number;
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

/** 백엔드의 공통 에러 응답. */
export interface ErrorResponse {
  code: string;
  message: string;
}
