"use client";

import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

export const TOTAL_STEP_COUNT = 7;

export type PrepState = "none" | "skimmed" | "review";

/** 휴식/수면 버퍼(전체의 15%)를 제외하고, 30~720분으로 clamp한다. */
const SLEEP_BUFFER_RATIO = 0.85;
const MIN_AVAILABLE = 30;
const MAX_AVAILABLE = 720;

type ExamInfoInput = {
  subject: string;
  range: string;
  examDate: string;
  examTime: string;
  prepState: PrepState;
};

type ExamState = ExamInfoInput & {
  /** 플랜을 생성(저장)한 시각. */
  plannedAt: number | null;
  /** 플랜 생성 시점에 확정된 공부 가능 시간(분). 이후 재계산하지 않는다. */
  availableMinutes: number | null;
  /**
   * "이 플랜이 어떤 시험 정보로 만들어졌는지"를 나타내는 서명.
   * usePlanStore.planSignature와 비교해 진행 상태(completedSteps 등)를
   * 초기화해야 하는지 판단하는 기준이 된다.
   */
  planSignature: string;
  setExamInfo: (info: ExamInfoInput) => void;
};

function computeAvailableMinutes(examDate: string, examTime: string): number | null {
  if (!examDate || !examTime) return null;
  const targetTs = new Date(`${examDate}T${examTime}:00`).getTime();
  if (Number.isNaN(targetTs)) return null;
  const rawMinutes = Math.floor((targetTs - Date.now()) / 60000);
  const buffered = Math.floor(rawMinutes * SLEEP_BUFFER_RATIO);
  return Math.max(MIN_AVAILABLE, Math.min(MAX_AVAILABLE, buffered));
}

export function computePlanSignature(info: ExamInfoInput): string {
  return `${info.examDate}|${info.examTime}|${info.prepState}|${info.subject}|${info.range}`;
}

export const useExamStore = create<ExamState>()(
  persist(
    (set) => ({
      subject: "",
      range: "",
      examDate: "",
      examTime: "",
      prepState: "none",
      plannedAt: null,
      availableMinutes: null,
      planSignature: computePlanSignature({ subject: "", range: "", examDate: "", examTime: "", prepState: "none" }),
      // 스냅샷 계산은 저장하는 이 순간에만 일어난다.
      setExamInfo: (info) =>
        set({
          ...info,
          plannedAt: Date.now(),
          availableMinutes: computeAvailableMinutes(info.examDate, info.examTime),
          planSignature: computePlanSignature(info),
        }),
    }),
    {
      name: "exam-info",
      storage: createJSONStorage(() => localStorage),
    }
  )
);

type PlanState = {
  /** 맵의 '이어서 공부하기'가 가리키는 1-indexed STEP. */
  currentStep: number;
  /** 퀴즈 통과로 완료된 STEP id 목록. */
  completedSteps: number[];
  /** 나중에 다시 볼 STEP id 목록. */
  weakSteps: number[];
  /** 이 진행 상태가 어떤 시험 정보(플랜)로 만들어졌는지. useExamStore.planSignature와 비교. */
  planSignature: string | null;
  completeStep: (id: number) => void;
  toggleWeakStep: (id: number) => void;
  advanceStep: (completedStepId: number) => void;
  setStep: (step: number) => void;
  /**
   * 새 플랜(시험 정보 변경)에 맞춰 진행 상태를 초기화한다.
   * weakSteps는 사용자가 직접 표시한 것이므로 유지하되, firstStepId가 살아있는 STEP이면
   * currentStep을 그 값으로, planSignature를 새 서명으로 갱신한다.
   */
  resetProgress: (firstStepId: number, nextSignature: string) => void;
  /** cut되어 사라진 STEP id를 weakSteps에서 제거한다. */
  pruneWeakSteps: (aliveStepIds: number[]) => void;
};

export const usePlanStore = create<PlanState>()(
  persist(
    (set) => ({
      currentStep: 3,
      completedSteps: [1, 2],
      weakSteps: [],
      planSignature: null,
      completeStep: (id) =>
        set((state) => ({
          completedSteps: state.completedSteps.includes(id)
            ? state.completedSteps
            : [...state.completedSteps, id].sort((a, b) => a - b),
          currentStep: Math.min(TOTAL_STEP_COUNT, Math.max(state.currentStep, id + 1)),
        })),
      toggleWeakStep: (id) =>
        set((state) => ({
          weakSteps: state.weakSteps.includes(id)
            ? state.weakSteps.filter((stepId) => stepId !== id)
            : [...state.weakSteps, id].sort((a, b) => a - b),
        })),
      advanceStep: (completedStepId) =>
        set((state) => ({
          currentStep: Math.min(
            TOTAL_STEP_COUNT,
            Math.max(state.currentStep, completedStepId + 1)
          ),
        })),
      setStep: (step) =>
        set({ currentStep: Math.min(TOTAL_STEP_COUNT, Math.max(1, step)) }),
      resetProgress: (firstStepId, nextSignature) =>
        set({
          completedSteps: [],
          currentStep: firstStepId,
          planSignature: nextSignature,
        }),
      pruneWeakSteps: (aliveStepIds) =>
        set((state) => ({
          weakSteps: state.weakSteps.filter((id) => aliveStepIds.includes(id)),
        })),
    }),
    {
      name: "plan-progress",
      storage: createJSONStorage(() => localStorage),
    }
  )
);
