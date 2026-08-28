import { describe, expect, it } from "vitest";
import {
  completedOrders,
  currentOrder,
  quizIdByNumber,
  stepIdByOrder,
  toBaseSteps,
  toCurriculumResult,
  toExamAt,
  toImportance,
  toQuestions,
  toStudyContent,
  toSourceLabel,
  toStepMode,
  topicIdByOrder,
} from "./adapt";
import type {
  CurriculumResponse,
  QuizListResponse,
  SessionResponse,
  StudyStepResponse,
  TopicResponse,
} from "./types";

/**
 * 어댑터 검증.
 *
 * 백엔드와 화면 사이에서 값이 어긋나면 예외가 나지 않고 조용히 틀린 화면이 나온다.
 * 그래서 변환 규칙 하나하나를 못박는다.
 */

function step(over: Partial<StudyStepResponse> = {}): StudyStepResponse {
  return {
    id: "11111111-0000-4000-8000-000000000001",
    order: 1,
    type: "STUDY",
    topicId: "22222222-0000-4000-8000-000000000001",
    title: "프로세스와 스레드",
    importance: "VERY_HIGH",
    originalEstimatedMinutes: 50,
    allocatedMinutes: 50,
    actualStudyMinutes: null,
    mandatory: false,
    priorityReasons: ["CORE_TOPIC"],
    status: "PENDING",
    startedAt: null,
    completedAt: null,
    ...over,
  };
}

function curriculum(steps: StudyStepResponse[]): CurriculumResponse {
  return {
    curriculumId: "33333333-0000-4000-8000-000000000001",
    initialRemainingMinutes: 180,
    totalAllocatedMinutes: steps.reduce((sum, s) => sum + s.allocatedMinutes, 0),
    status: "CREATED",
    progress: { completedSteps: 0, totalSteps: steps.length, percentage: 0 },
    steps,
  };
}

describe("toImportance — 중요도 4단계를 화면의 3단계로", () => {
  it("VERY_HIGH 와 HIGH 를 같은 high 로 묶는다", () => {
    // 화면에 두 단계를 구분해 보여줄 자리가 없다. 우선순위 판단은 이미 서버에서 끝났다.
    expect(toImportance("VERY_HIGH")).toBe("high");
    expect(toImportance("HIGH")).toBe("high");
  });

  it("MEDIUM 은 mid, LOW 는 low", () => {
    expect(toImportance("MEDIUM")).toBe("mid");
    expect(toImportance("LOW")).toBe("low");
  });

  it("복습 단계처럼 중요도가 없으면 low 로 둔다", () => {
    expect(toImportance(null)).toBe("low");
  });
});

describe("toBaseSteps — Topic 목록", () => {
  const topic = (over: Partial<TopicResponse>): TopicResponse => ({
    id: "t",
    title: "제목",
    summary: "요약",
    keyPoints: ["개념"],
    importance: "MEDIUM",
    estimatedStudyMinutes: 30,
    professorEmphasisMatched: false,
    pastExamMatched: false,
    weakAreaMatched: false,
    mustStudyMatched: false,
    topicOrder: 1,
    ...over,
  });

  it("topicOrder 순으로 정렬한다", () => {
    const steps = toBaseSteps([
      topic({ topicOrder: 3, title: "셋" }),
      topic({ topicOrder: 1, title: "하나" }),
      topic({ topicOrder: 2, title: "둘" }),
    ]);
    expect(steps.map((s) => s.title)).toEqual(["하나", "둘", "셋"]);
  });

  it("id 는 topicOrder 를 쓴다 — 라우트가 숫자를 쓰기 때문", () => {
    const steps = toBaseSteps([topic({ topicOrder: 4 })]);
    expect(steps[0].id).toBe(4);
  });

  it("입력 배열을 건드리지 않는다", () => {
    const input = [topic({ topicOrder: 2 }), topic({ topicOrder: 1 })];
    toBaseSteps(input);
    expect(input[0].topicOrder).toBe(2);
  });
});

describe("toStepMode — 두 시간 값의 관계가 곧 mode", () => {
  it("배정 시간이 그대로면 full", () => {
    expect(toStepMode(step({ originalEstimatedMinutes: 50, allocatedMinutes: 50 }))).toBe("full");
  });

  it("60% 이하로 눌렸으면 skim", () => {
    expect(toStepMode(step({ originalEstimatedMinutes: 50, allocatedMinutes: 30 }))).toBe("skim");
    expect(toStepMode(step({ originalEstimatedMinutes: 40, allocatedMinutes: 12 }))).toBe("skim");
  });

  it("그 사이면 review", () => {
    expect(toStepMode(step({ originalEstimatedMinutes: 55, allocatedMinutes: 43 }))).toBe("review");
  });

  it("REVIEW 단계는 시간과 무관하게 review", () => {
    expect(toStepMode(step({ type: "REVIEW", allocatedMinutes: 30, originalEstimatedMinutes: 30 })))
      .toBe("review");
  });

  it("원래 시간이 0이어도 나누기 오류를 내지 않는다", () => {
    expect(toStepMode(step({ originalEstimatedMinutes: 0, allocatedMinutes: 0 }))).toBe("full");
  });
});

describe("toCurriculumResult — 학습 계획", () => {
  it("배정 시간과 원래 시간을 각각 옮긴다", () => {
    const result = toCurriculumResult(
      curriculum([step({ order: 1, originalEstimatedMinutes: 55, allocatedMinutes: 43 })])
    );
    expect(result.steps[0]).toMatchObject({ minutes: 43, baseMinutes: 55 });
  });

  it("SKIPPED 단계는 목록에서 빼고 cutStepIds 로 넘긴다", () => {
    // 남은 할 일 목록에 섞여 있으면 사용자가 아직 해야 할 일로 오해한다.
    const result = toCurriculumResult(
      curriculum([
        step({ order: 1, allocatedMinutes: 50 }),
        step({ order: 2, allocatedMinutes: 0, status: "SKIPPED" }),
        step({ order: 3, allocatedMinutes: 40 }),
      ])
    );
    expect(result.steps.map((s) => s.id)).toEqual([1, 3]);
    expect(result.cutStepIds).toEqual([2]);
  });

  it("총 시간은 살아 있는 단계만 더한다", () => {
    const result = toCurriculumResult(
      curriculum([
        step({ order: 1, allocatedMinutes: 50 }),
        step({ order: 2, allocatedMinutes: 0, status: "SKIPPED", originalEstimatedMinutes: 40 }),
      ])
    );
    expect(result.totalMinutes).toBe(50);
  });

  it("감축률은 잘린 단계까지 포함한 원래 시간을 기준으로 한다", () => {
    // 원래 100분이 필요했는데 50분만 배정 → 50% 감축
    const result = toCurriculumResult(
      curriculum([
        step({ order: 1, originalEstimatedMinutes: 60, allocatedMinutes: 50 }),
        step({ order: 2, originalEstimatedMinutes: 40, allocatedMinutes: 0, status: "SKIPPED" }),
      ])
    );
    expect(result.baseTotalMinutes).toBe(100);
    expect(result.reductionPct).toBe(50);
  });

  it("단계가 없어도 0으로 나누지 않는다", () => {
    const result = toCurriculumResult(curriculum([]));
    expect(result.reductionPct).toBe(0);
    expect(result.steps).toEqual([]);
  });
});

describe("id 표 — 숫자 라우트와 서버 UUID 를 잇는다", () => {
  const plan = curriculum([
    step({ order: 1, id: "step-1", topicId: "topic-1" }),
    step({ order: 2, id: "step-2", topicId: "topic-2" }),
    step({ order: 3, id: "step-3", topicId: null, type: "REVIEW" }),
  ]);

  it("order 로 스텝 UUID 를 찾는다", () => {
    expect(stepIdByOrder(plan).get(2)).toBe("step-2");
  });

  it("SKIPPED 단계도 표에는 남긴다 — 상세를 열어볼 수 있어야 한다", () => {
    const withSkipped = curriculum([step({ order: 9, id: "step-9", status: "SKIPPED" })]);
    expect(stepIdByOrder(withSkipped).get(9)).toBe("step-9");
  });

  it("복습 단계는 Topic 이 없어 표에서 빠진다", () => {
    const topics = topicIdByOrder(plan);
    expect(topics.get(1)).toBe("topic-1");
    expect(topics.has(3)).toBe(false);
  });
});

describe("진행 상태", () => {
  it("완료된 단계의 order 를 순서대로 준다", () => {
    const plan = curriculum([
      step({ order: 2, status: "COMPLETED" }),
      step({ order: 1, status: "COMPLETED" }),
      step({ order: 3, status: "PENDING" }),
    ]);
    expect(completedOrders(plan)).toEqual([1, 2]);
  });

  it("진행 중인 단계가 현재 단계다", () => {
    const plan = curriculum([
      step({ order: 1, status: "COMPLETED" }),
      step({ order: 2, status: "IN_PROGRESS" }),
      step({ order: 3, status: "PENDING" }),
    ]);
    expect(currentOrder(plan)).toBe(2);
  });

  it("진행 중이 없으면 남은 것 중 가장 앞선 단계", () => {
    const plan = curriculum([
      step({ order: 1, status: "COMPLETED" }),
      step({ order: 2, status: "PENDING" }),
    ]);
    expect(currentOrder(plan)).toBe(2);
  });

  it("다 끝났으면 마지막 단계를 가리킨다 — 빈 화면이 되지 않게", () => {
    const plan = curriculum([
      step({ order: 1, status: "COMPLETED" }),
      step({ order: 2, status: "COMPLETED" }),
    ]);
    expect(currentOrder(plan)).toBe(2);
  });

  it("단계가 하나도 없어도 1을 준다", () => {
    expect(currentOrder(curriculum([]))).toBe(1);
  });
});

describe("toQuestions — 정답을 화면에 내려보내지 않는다", () => {
  const list: QuizListResponse = {
    topicId: "topic-1",
    topicTitle: "프로세스와 스레드",
    quizzes: [
      { id: "q-2", order: 2, question: "두 번째", options: ["a", "b", "c", "d"], difficulty: "MEDIUM" },
      { id: "q-1", order: 1, question: "첫 번째", options: ["a", "b", "c", "d"], difficulty: "EASY" },
    ],
  };

  it("order 순으로 정렬한다", () => {
    expect(toQuestions(list).map((q) => q.question)).toEqual(["첫 번째", "두 번째"]);
  });

  it("정답과 해설이 비어 있다 — 채점은 서버가 한다", () => {
    // 정답을 응답에 실으면 개발자 도구로 다 보인다.
    const questions = toQuestions(list);
    expect(questions.every((q) => q.answerIndex === -1)).toBe(true);
    expect(questions.every((q) => q.explanation === "")).toBe(true);
  });

  it("숫자 id 와 서버 UUID 를 이어 준다", () => {
    const ids = quizIdByNumber(list);
    expect(ids.get(1)).toBe("q-1");
    expect(ids.get(2)).toBe("q-2");
  });
});

describe("toExamAt — 시간대 표기를 붙이지 않는다", () => {
  it("날짜와 시각을 그대로 잇는다", () => {
    expect(toExamAt("2026-08-29", "09:00")).toBe("2026-08-29T09:00:00");
  });

  it("초가 이미 있으면 덧붙이지 않는다", () => {
    expect(toExamAt("2026-08-29", "09:00:30")).toBe("2026-08-29T09:00:30");
  });

  it("toISOString 을 쓰지 않는다 — UTC 로 바뀌면 9시간 어긋난다", () => {
    // 백엔드는 시간대 없는 LocalDateTime 을 받고, 그것을 한국 시각으로 해석한다.
    const result = toExamAt("2026-08-29", "09:00");
    expect(result).not.toContain("Z");
    expect(result).not.toContain("+");
  });
});

describe("toStudyContent — 없는 값을 지어내지 않는다", () => {
  const base: TopicResponse = {
    id: "t-1",
    title: "CPU 스케줄링",
    summary: "준비 큐에서 다음 프로세스를 고르는 방법이다.",
    keyPoints: ["FCFS", "SJF", "라운드 로빈"],
    importance: "VERY_HIGH",
    estimatedStudyMinutes: 45,
    professorEmphasisMatched: false,
    pastExamMatched: false,
    weakAreaMatched: false,
    mustStudyMatched: false,
    topicOrder: 2,
  };

  it("요약과 핵심 개념을 그대로 옮긴다", () => {
    const content = toStudyContent(base);
    expect(content.summary).toBe(base.summary);
    expect(content.concepts.map((c) => c.title)).toEqual(["FCFS", "SJF", "라운드 로빈"]);
  });

  it("서버에 없는 값은 빈 문자열로 둔다", () => {
    // 챕터명·시험 팁은 자료마다 달라 서버가 알 수 없다.
    // 그럴듯하게 지어내면 사용자가 자료에 있는 내용으로 오해한다.
    const content = toStudyContent(base);
    expect(content.chapter).toBe("");
    expect(content.examTip).toBe("");
    expect(content.characterComment).toBe("");
  });

  it("중요도를 한국어 라벨로 바꾼다", () => {
    expect(toStudyContent(base).importanceLabel).toBe("매우 높음");
    expect(toStudyContent({ ...base, importance: "LOW" }).importanceLabel).toBe("낮음");
  });

  it("실제로 맞은 학습 맥락만 근거로 적는다", () => {
    const matched = toStudyContent({
      ...base,
      mustStudyMatched: true,
      pastExamMatched: true,
    });
    expect(matched.importanceNote).toBe("반드시 볼 범위 · 기출 관련");
  });

  it("맞은 맥락이 없으면 비워 둔다", () => {
    expect(toStudyContent(base).importanceNote).toBe("");
  });

  it("id 는 topicOrder 를 쓴다 — 라우트와 같은 기준", () => {
    expect(toStudyContent(base).id).toBe(2);
  });
});

describe("toSourceLabel", () => {
  function session(over: Partial<SessionResponse> = {}): SessionResponse {
    return {
      sessionCode: "A1B2C3D4",
      subject: "자료구조",
      examScope: "3장 스택 ~ 7장 그래프",
      grounded: false,
      sourceType: null,
      examAt: "2026-08-29T09:00:00",
      availableStudyMinutes: 180,
      remainingStudyMinutes: 150,
      status: "READY",
      ...over,
    };
  }

  it("분석 전에는 표시할 근거가 없다", () => {
    expect(toSourceLabel(session())).toBeNull();
    expect(toSourceLabel(null)).toBeNull();
  });

  it("자료 기반이면 안내 문구를 붙이지 않는다", () => {
    const label = toSourceLabel(session({ sourceType: "USER_MATERIAL", grounded: true }));
    expect(label).toEqual({ grounded: true, label: "학습자료 기반", notice: null });
  });

  it("일반 지식 기반이면 차이가 있을 수 있다고 알린다", () => {
    const label = toSourceLabel(session({ sourceType: "GENERAL_KNOWLEDGE" }));
    expect(label?.grounded).toBe(false);
    expect(label?.label).toBe("일반 지식 기반");
    // 문구를 바꾸면 이 테스트가 깨진다. 사용자에게 하는 고지라 조용히 사라지면 안 된다.
    expect(label?.notice).toBe(
      "업로드된 학습자료가 없어 일반적인 교과 지식을 기준으로 생성되었습니다. 실제 수업 범위와 일부 차이가 있을 수 있습니다."
    );
  });
});
