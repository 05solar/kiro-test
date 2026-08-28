"use client";

import { useCallback, useEffect, useState } from "react";
import { getCurriculum, toMessage, type CurriculumResponse } from "@/lib/api";
import {
  completedOrders,
  currentOrder,
  stepIdByOrder,
  toCurriculumResult,
  topicIdByOrder,
} from "@/lib/api/adapt";
import type { CurriculumResult } from "@/lib/curriculum";
import { useSessionStore } from "./session-store";

/**
 * 서버에서 학습 계획을 읽어 화면 타입으로 준다.
 *
 * <p><b>계획과 진행 상태는 서버가 갖는다.</b> 화면이 따로 계산하거나 저장하지 않는다.
 * 양쪽이 각자 시간을 배분하면 화면에 보이는 값과 서버가 아는 값이 어긋나고,
 * 다른 기기에서 접속했을 때 어느 쪽이 맞는지 판단할 근거가 없다.
 *
 * <p>단계를 시작하거나 완료한 뒤에는 {@link CurriculumView.reload} 를 부른다.
 * 완료 시 서버가 남은 단계의 배정 시간을 다시 나누므로, 다시 읽지 않으면
 * 화면에 옛 시간이 남는다.
 */
export type CurriculumView = {
  /** 아직 서버 응답을 받지 못한 상태. */
  loading: boolean;
  /** 화면에 그대로 띄울 수 있는 실패 문구. 없으면 null. */
  error: string | null;
  /** 계획이 아직 없다(404). 분석까지는 끝났지만 계획을 만들지 않은 경우. */
  missing: boolean;
  raw: CurriculumResponse | null;
  plan: CurriculumResult | null;
  completedStepIds: number[];
  currentStepId: number;
  /** 화면의 숫자 stepId → 서버 스텝 UUID */
  stepIds: Map<number, string>;
  /** 화면의 숫자 stepId → 그 단계가 다루는 Topic UUID */
  topicIds: Map<number, string>;
  reload: () => Promise<void>;
};

export function useCurriculum(): CurriculumView {
  const sessionCode = useSessionStore((state) => state.sessionCode);
  const [raw, setRaw] = useState<CurriculumResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [missing, setMissing] = useState(false);

  const load = useCallback(async () => {
    if (!sessionCode) {
      setLoading(false);
      setMissing(true);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setRaw(await getCurriculum(sessionCode));
      setMissing(false);
    } catch (e) {
      // 계획이 아직 없는 것과 진짜 실패는 화면에서 다르게 다뤄야 한다.
      // 전자는 "계획 만들기"로 보내면 되고, 후자는 오류를 보여줘야 한다.
      if (isCurriculumMissing(e)) {
        setMissing(true);
        setRaw(null);
      } else {
        setError(toMessage(e));
      }
    } finally {
      setLoading(false);
    }
  }, [sessionCode]);

  useEffect(() => {
    void load();
  }, [load]);

  return {
    loading,
    error,
    missing,
    raw,
    plan: raw ? toCurriculumResult(raw) : null,
    completedStepIds: raw ? completedOrders(raw) : [],
    currentStepId: raw ? currentOrder(raw) : 1,
    stepIds: raw ? stepIdByOrder(raw) : new Map(),
    topicIds: raw ? topicIdByOrder(raw) : new Map(),
    reload: load,
  };
}

function isCurriculumMissing(error: unknown): boolean {
  return (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    (error as { code: unknown }).code === "CURRICULUM_NOT_FOUND"
  );
}
