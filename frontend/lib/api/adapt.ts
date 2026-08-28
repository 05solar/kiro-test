import type { BaseStep, CurriculumResult, CurriculumStep, Importance, StepMode } from "@/lib/curriculum";
import type { Question } from "@/lib/quiz";
import type {
  CurriculumResponse,
  QuizListResponse,
  SessionResponse,
  StudyStepResponse,
  TopicImportance,
  TopicResponse,
} from "./types";

/**
 * 백엔드 응답을 화면이 쓰던 타입으로 바꾼다.
 *
 * <p>이 파일이 두 시스템의 접합면이다. 화면 컴포넌트는 백엔드 DTO 를 직접 모른다.
 * 서버 계약이 바뀌면 여기만 고치면 되고, 어긋났을 때 어디를 봐야 하는지도 분명하다.
 *
 * <p><b>계획은 서버가 만든다.</b> 프론트의 `generateCurriculum()` 은 더 이상 실제 데이터에
 * 쓰지 않는다. 양쪽이 각자 시간을 계산하면 화면에 보이는 남은 시간과 서버가 아는 값이
 * 어긋나고, 그때 어느 쪽이 맞는지 판단할 근거가 없다.
 */

/**
 * 중요도 네 단계를 화면의 세 단계로 줄인다.
 *
 * <p>화면은 high/mid/low 세 가지 색과 라벨만 갖고 있다.
 * `VERY_HIGH` 와 `HIGH` 를 나누어 보여줄 자리가 없어 둘 다 high 로 묶는다.
 * 우선순위 판단은 이미 서버에서 끝났으므로 여기서 정보가 줄어도 계획은 바뀌지 않는다.
 */
export function toImportance(importance: TopicImportance | null): Importance {
  switch (importance) {
    case "VERY_HIGH":
    case "HIGH":
      return "high";
    case "MEDIUM":
      return "mid";
    default:
      return "low";
  }
}

/** 분석 결과(Topic)를 화면의 기본 스텝 목록으로. 아직 시간 배분 전 상태다. */
export function toBaseSteps(topics: TopicResponse[]): BaseStep[] {
  return [...topics]
    .sort((a, b) => a.topicOrder - b.topicOrder)
    .map((topic) => ({
      id: topic.topicOrder,
      title: topic.title,
      importance: toImportance(topic.importance),
      baseMinutes: topic.estimatedStudyMinutes,
    }));
}

/**
 * 배정 시간이 원래 필요 시간보다 줄었으면 축약 학습으로 본다.
 *
 * <p>서버에는 mode 개념이 없다. 두 시간 값의 관계가 곧 mode 다.
 * 60% 이하로 눌렸으면 훑기, 그 사이면 축약, 그대로면 전체 학습이다.
 */
export function toStepMode(step: StudyStepResponse): StepMode {
  if (step.type === "REVIEW") return "review";
  if (step.originalEstimatedMinutes <= 0) return "full";
  const ratio = step.allocatedMinutes / step.originalEstimatedMinutes;
  if (ratio <= 0.6) return "skim";
  if (ratio < 1) return "review";
  return "full";
}

/**
 * 학습 계획 응답을 화면의 `CurriculumResult` 로.
 *
 * <p>`SKIPPED` 단계는 목록에서 빼고 `cutStepIds` 에 담는다. 화면은 잘린 단계를
 * 따로 보여주므로 섞어 두면 사용자가 아직 해야 할 일로 오해한다.
 *
 * <p>스텝 id 는 서버의 `order` 를 쓴다. 화면 라우트가 `/study/1` 처럼 숫자를 쓰고 있어
 * UUID 를 그대로 넣으면 주소가 읽을 수 없게 된다. UUID 는 `stepIdByOrder` 로 따로 넘긴다.
 */
export function toCurriculumResult(curriculum: CurriculumResponse): CurriculumResult {
  const alive = curriculum.steps.filter((step) => step.status !== "SKIPPED");
  const cut = curriculum.steps.filter((step) => step.status === "SKIPPED");

  const steps: CurriculumStep[] = alive.map((step) => ({
    id: step.order,
    title: step.title,
    importance: toImportance(step.importance),
    minutes: step.allocatedMinutes,
    baseMinutes: step.originalEstimatedMinutes,
    mode: toStepMode(step),
    // 서버는 퀴즈 우선 여부를 정하지 않는다. 퀴즈는 학습을 마쳐야 만들 수 있으므로 항상 false.
    quizFirst: false,
  }));

  const totalMinutes = steps.reduce((sum, step) => sum + step.minutes, 0);
  const baseTotalMinutes = curriculum.steps.reduce(
    (sum, step) => sum + step.originalEstimatedMinutes,
    0
  );

  return {
    steps,
    totalMinutes,
    baseTotalMinutes,
    reductionPct:
      baseTotalMinutes > 0 ? Math.round((1 - totalMinutes / baseTotalMinutes) * 100) : 0,
    cutStepIds: cut.map((step) => step.order),
  };
}

/** 화면의 숫자 stepId(= 서버의 order)로 서버의 UUID 를 찾기 위한 표. */
export function stepIdByOrder(curriculum: CurriculumResponse): Map<number, string> {
  return new Map(curriculum.steps.map((step) => [step.order, step.id]));
}

/** 스텝 order 로 그 스텝이 다루는 Topic 의 UUID 를 찾는 표. 복습 단계는 Topic 이 없다. */
export function topicIdByOrder(curriculum: CurriculumResponse): Map<number, string> {
  const entries = curriculum.steps
    .filter((step): step is StudyStepResponse & { topicId: string } => step.topicId !== null)
    .map((step) => [step.order, step.topicId] as const);
  return new Map(entries);
}

/** 완료된 스텝의 order 목록. 화면의 진행 상태와 같은 기준을 쓴다. */
export function completedOrders(curriculum: CurriculumResponse): number[] {
  return curriculum.steps
    .filter((step) => step.status === "COMPLETED")
    .map((step) => step.order)
    .sort((a, b) => a - b);
}

/**
 * 지금 진행할 스텝의 order.
 *
 * <p>진행 중인 단계가 있으면 그것, 없으면 남은 것 중 가장 앞선 것.
 * 다 끝났으면 마지막 단계를 가리킨다 — 화면이 빈 상태가 되지 않게.
 */
export function currentOrder(curriculum: CurriculumResponse): number {
  const inProgress = curriculum.steps.find((step) => step.status === "IN_PROGRESS");
  if (inProgress) return inProgress.order;
  const pending = curriculum.steps.find((step) => step.status === "PENDING");
  if (pending) return pending.order;
  const last = curriculum.steps.at(-1);
  return last?.order ?? 1;
}

/**
 * 퀴즈 목록을 화면 타입으로.
 *
 * <p><b>`answerIndex` 는 -1 이다.</b> 서버는 답안을 내기 전까지 정답을 내려주지 않는다.
 * 정답을 응답에 실으면 개발자 도구로 다 보인다. 채점은 서버가 하고, 화면은
 * 답안 제출 응답으로 정오답을 받는다.
 *
 * <p>`explanation` 도 같은 이유로 비어 있다. 제출 후에 채워진다.
 */
export function toQuestions(list: QuizListResponse): Question[] {
  return [...list.quizzes]
    .sort((a, b) => a.order - b.order)
    .map((quiz, index) => ({
      // 화면이 숫자 id 를 쓰므로 순번을 넣고, 서버 UUID 는 quizIds 로 따로 넘긴다.
      id: index + 1,
      question: quiz.question,
      options: [...quiz.options],
      answerIndex: -1,
      explanation: "",
    }));
}

/**
 * Topic 을 학습 화면이 쓰는 형태로.
 *
 * <p>서버에 없는 값은 만들어 내지 않는다. 원래 화면에는 챕터명·시험 팁·캐릭터 대사가
 * 하드코딩돼 있었는데, 자료마다 달라지는 값이라 서버가 알 수 없다.
 * 없는 것을 그럴듯하게 지어내면 사용자가 자료에 있는 내용으로 오해한다.
 *
 * <p>핵심 개념(keyPoints)은 제목만 있고 설명이 없다. 화면은 설명이 비어도 견딘다.
 */
export function toStudyContent(topic: TopicResponse) {
  return {
    id: topic.topicOrder,
    title: topic.title,
    chapter: "",
    importanceLabel: importanceLabelOf(topic.importance),
    importanceNote: matchNote(topic),
    summary: topic.summary,
    concepts: topic.keyPoints.map((keyPoint) => ({ title: keyPoint, description: "" })),
    examTip: "",
    characterComment: "",
  };
}

function importanceLabelOf(importance: TopicImportance): string {
  switch (importance) {
    case "VERY_HIGH":
      return "매우 높음";
    case "HIGH":
      return "높음";
    case "MEDIUM":
      return "보통";
    default:
      return "낮음";
  }
}

/**
 * 이 주제가 왜 중요한지 서버가 판단한 근거.
 *
 * <p>사용자가 입력한 학습 맥락과 실제로 맞은 항목만 적는다.
 * 맞은 것이 없으면 빈 문자열이다 — 없는 근거를 지어내지 않는다.
 */
function matchNote(topic: TopicResponse): string {
  const reasons: string[] = [];
  if (topic.mustStudyMatched) reasons.push("반드시 볼 범위");
  if (topic.professorEmphasisMatched) reasons.push("교수님 강조");
  if (topic.pastExamMatched) reasons.push("기출 관련");
  if (topic.weakAreaMatched) reasons.push("자신 없는 부분");
  return reasons.join(" · ");
}

/** 화면의 숫자 문제 id 로 서버의 퀴즈 UUID 를 찾는 표. */
export function quizIdByNumber(list: QuizListResponse): Map<number, string> {
  return new Map(
    [...list.quizzes]
      .sort((a, b) => a.order - b.order)
      .map((quiz, index) => [index + 1, quiz.id] as const)
  );
}

/**
 * 화면이 시험 시각을 보내는 형태로 바꾼다.
 *
 * <p>백엔드는 시간대 표기가 없는 `2026-08-29T09:00:00` 을 받는다.
 * `toISOString()` 을 쓰면 UTC 로 바뀌어 9시간 어긋나므로 쓰지 않는다.
 * 화면의 날짜/시각 입력값이 이미 한국 시각이므로 그대로 이어 붙인다.
 */
export function toExamAt(examDate: string, examTime: string): string {
  return `${examDate}T${examTime.length === 5 ? `${examTime}:00` : examTime}`;
}

/**
 * 학습 내용이 무엇에 근거해 만들어졌는지.
 *
 * <p>자료를 올리지 않으면 서버는 과목명과 시험 범위만 보고 일반적인 교과 지식으로
 * 주제를 만든다. 그렇게 만든 계획과 퀴즈는 실제 수업 범위와 다를 수 있다.
 * 화면이 이 차이를 감추면 사용자는 자기 강의자료에서 뽑은 내용이라고 믿게 된다.
 *
 * <p>분석 전에는 근거가 아직 없으므로 null 을 준다 — 아무것도 표시하지 않는다.
 */
export type SourceLabel = {
  grounded: boolean;
  /** 화면에 붙이는 짧은 표시. */
  label: string;
  /** 일반 지식으로 만들었을 때만 있는 안내 문구. 자료 기반이면 null. */
  notice: string | null;
};

const GENERAL_KNOWLEDGE_NOTICE =
  "업로드된 학습자료가 없어 일반적인 교과 지식을 기준으로 생성되었습니다. 실제 수업 범위와 일부 차이가 있을 수 있습니다.";

export function toSourceLabel(session: SessionResponse | null | undefined): SourceLabel | null {
  if (!session?.sourceType) return null;
  return session.sourceType === "USER_MATERIAL"
    ? { grounded: true, label: "학습자료 기반", notice: null }
    : { grounded: false, label: "일반 지식 기반", notice: GENERAL_KNOWLEDGE_NOTICE };
}
