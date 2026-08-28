"use client";

import { useEffect, useId, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { AppHeader, CheckIcon, CheckMini, Ghost, PrimaryButton } from "@/app/_components/ui";
import { useExamStore, usePlanStore } from "@/app/_components/store";
import { useCurriculum } from "@/app/_components/use-curriculum";
import { useHydrated } from "@/app/_components/use-hydrated";
import {
  formatMinutes,
  type CurriculumResult,
  type CurriculumStep,
  type PrepState,
} from "@/lib/curriculum";
import { rewardAfterSteps, TRACK_PATH_D } from "@/lib/mapNodes";

const PREP_LABEL: Record<PrepState, string> = {
  none: "아예 안 봤어요",
  skimmed: "한번 훑었어요",
  review: "복습만 필요해요",
};

const MODE_LABEL: Record<string, string> = {
  full: "개념 학습",
  skim: "빠르게 훑기",
  review: "복습·퀴즈",
};

// 원본 viewBox(900x600)는 그대로 두고, 시험장 도착 시 캐릭터·말풍선이
// 위쪽으로 잘리지 않도록 세로 여백만 추가한다(가로는 손대지 않아 스크롤바가 새로 생기지 않음).
const PAD_TOP = 100;
const VIEW_W = 900;
const VIEW_H = 600 + PAD_TOP;

export default function CurriculumPage() {
  const router = useRouter();
  const hydrated = useHydrated();
  // weakSteps 는 사용자가 직접 표시한 값이라 서버에 없다. 화면에만 남긴다.
  const rawWeakSteps = usePlanStore((state) => state.weakSteps);
  const pruneWeakSteps = usePlanStore((state) => state.pruneWeakSteps);
  const weakSteps = hydrated ? rawWeakSteps : [];

  const rawPrepState = useExamStore((state) => state.prepState);
  const examDate = useExamStore((state) => state.examDate);
  const examTime = useExamStore((state) => state.examTime);
  const prepState: PrepState = hydrated ? rawPrepState : "none";

  /*
   * 계획과 진행 상태는 서버에서 읽는다.
   *
   * 예전에는 화면이 generateCurriculum() 으로 직접 시간을 배분했다. 서버에도 같은 일을 하는
   * 계획기가 있어서, 둘이 각자 계산하면 화면에 보이는 남은 시간과 서버가 아는 값이 어긋난다.
   * 그때 어느 쪽이 맞는지 판단할 근거가 없으므로 서버 하나로 정한다.
   */
  const curriculum = useCurriculum();
  const emptyPlan: CurriculumResult = {
    steps: [],
    totalMinutes: 0,
    baseTotalMinutes: 0,
    reductionPct: 0,
    cutStepIds: [],
  };
  const plan = curriculum.plan ?? emptyPlan;
  const completedStepIds = curriculum.completedStepIds;
  const availableMinutes = curriculum.raw?.initialRemainingMinutes ?? null;

  // 맵 진행 순서(=STEP 번호 순서)용 배열. plan.steps는 skimmed 모드일 때
  // "핵심 우선"으로 재배치되므로, "다음에 갈 스텝"을 판단하는 모든 곳은
  // 이 orderedSteps만 사용한다. plan.steps는 우측 STEP 목록 표시에만 쓴다.
  const orderedSteps = [...plan.steps].sort((a, b) => a.id - b.id);
  const nextStep = orderedSteps.find((step) => !completedStepIds.includes(step.id)) ?? null;
  // 전부 완료면 nextStep이 없다. 이 경우 잠금 판정에 영향이 없도록 +Infinity로 둔다
  // (goalReached가 항상 true이므로 실제로 이 값을 참조하는 UI는 없다).
  const currentStepId = nextStep?.id ?? Number.POSITIVE_INFINITY;

  /*
   * 진행 상태 초기화는 더 이상 화면이 하지 않는다.
   *
   * 완료한 단계가 무엇인지는 서버가 안다. 시험 정보를 바꿔 계획을 다시 만들면
   * 서버 쪽 단계가 통째로 바뀌므로, 화면은 그때그때 읽어 오기만 하면 된다.
   * planSignature 로 로컬 진행 상태를 맞추던 코드는 그래서 필요 없어졌다.
   */

  // 잘린(SKIPPED) STEP 은 '다시 볼 개념' 표시에서도 빼 준다.
  // 그 목록만은 사용자가 직접 만든 것이라 서버에 없다.
  useEffect(() => {
    if (!hydrated || !curriculum.raw) return;
    const aliveIds = plan.steps.map((step) => step.id);
    if (weakSteps.some((id) => !aliveIds.includes(id))) {
      pruneWeakSteps(aliveIds);
    }
    // weakSteps/plan.steps 는 매 렌더 새 배열이라 deps 에 넣으면 루프가 된다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hydrated, curriculum.raw]);

  const [nowTs, setNowTs] = useState<number | null>(null);
  useEffect(() => {
    const update = () => setNowTs(Date.now());
    update();
    const id = window.setInterval(update, 30000);
    return () => window.clearInterval(id);
  }, []);

  const isPastExam = (() => {
    if (!hydrated || nowTs === null || !examDate || !examTime) return false;
    const targetTs = new Date(`${examDate}T${examTime}:00`).getTime();
    return !Number.isNaN(targetTs) && targetTs - nowTs <= 0;
  })();

  // 남은 예상 시간은 "아직 완료하지 않은" STEP만 합산한다.
  // (currentStep 이상이 아니라 completedStepIds 기준이어야 100% 완료 시 정확히 0분이 된다)
  const remainingMinutes = plan.steps
    .filter((step) => !completedStepIds.includes(step.id))
    .reduce((total, step) => total + step.minutes, 0);

  const totalSteps = plan.steps.length;
  const completedCount = plan.steps.filter((step) => completedStepIds.includes(step.id)).length;
  const percent = totalSteps > 0 ? Math.round((completedCount / totalSteps) * 100) : 0;

  // GOAL 도달: cut 반영된 plan의 모든 STEP이 완료됐을 때만 true (상수 사용 안 함).
  const goalReached = totalSteps > 0 && orderedSteps.every((step) => completedStepIds.includes(step.id));

  // 상자는 좌표가 아니라 규칙으로 놓는다 — 실제 STEP 수가 계획마다 다르기 때문이다.
  const rewardAfter = rewardAfterSteps(orderedSteps.map((step) => step.id));
  const openedRewardCount = rewardAfter.filter((id) => completedStepIds.includes(id)).length;

  /*
   * 잘린 STEP 과 '다시 볼 개념' 목록은 서버가 준 전체 단계에서 찾는다.
   *
   * 예전에는 하드코딩된 BASE_STEPS 에서 찾았다. 서버가 주는 단계는 자료마다 달라서
   * 그 표에는 없다.
   */
  const allStepTitles = new Map<number, string>(
    (curriculum.raw?.steps ?? []).map((step) => [step.order, step.title])
  );

  const cutSteps = plan.cutStepIds.map((id) => ({
    id,
    title: allStepTitles.get(id) ?? `STEP ${id}`,
  }));
  const [showCut, setShowCut] = useState(false);


  const allDone = goalReached;
  const firstWeak = weakSteps[0];

  return (
    <div className="min-h-screen bg-white text-[#222]">
      <AppHeader />
      <main className="mx-auto max-w-[1440px] px-5 pb-20 pt-11 sm:px-10">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-6">
          <div>
            <div className="mb-[9px] text-[13px] font-bold text-[#FF7A00]">자료구조 · 3장 ~ 7장</div>
            <h1 className="font-jua mb-[7px] text-4xl tracking-[-1px]">오늘 밤 벼락치기 맵</h1>
            <p className="text-[15px] text-[#888]">STEP {totalSteps}개 · 상자 {rewardAfter.length}개</p>
            {prepState === "review" && (
              <p className="mt-1 text-[13px] font-bold text-[#E85D00]">복습 모드 · 퀴즈 위주로 빠르게</p>
            )}
          </div>
          {allDone ? (
            <PrimaryButton
              className="px-7 py-[15px] text-[15.5px]"
              disabled={!firstWeak}
              onClick={() => firstWeak && router.push(`/study/${firstWeak}`)}
            >
              {firstWeak ? "다시 볼 개념 복습하기" : "학습 완료!"}
            </PrimaryButton>
          ) : (
            <PrimaryButton
              className="px-7 py-[15px] text-[15.5px]"
              onClick={() => nextStep && router.push(`/study/${nextStep.id}`)}
            >
              {completedCount === 0 ? `STEP ${currentStepId} 시작하기` : `STEP ${currentStepId} 이어서 공부하기`}
            </PrimaryButton>
          )}
        </div>

        <div className="mb-4 inline-flex flex-wrap items-center gap-x-2 gap-y-1 rounded-full border border-[#FFE0C4] bg-[#FFF3E8] px-4 py-2 text-[13px]">
          {hydrated ? (
            <>
              <span className="font-bold text-[#E85D00]">선택: {PREP_LABEL[prepState]}</span>
              <span className="text-[#888]">·</span>
              <span className="text-[#222]">예상 {formatMinutes(plan.totalMinutes)}</span>
              {plan.reductionPct > 0 && (
                <span className="font-bold text-[#FF7A00]">(기본 대비 -{plan.reductionPct}%)</span>
              )}
            </>
          ) : (
            <span className="text-[#888]">플랜 요약 계산 중…</span>
          )}
        </div>

        {isPastExam && (
          <div className="mb-4 rounded-xl border border-[#FFD0D0] bg-[#FFF3F3] px-4 py-3 text-[13.5px] font-bold text-[#D14343]">
            시험 시간이 지났습니다. 다음 시험 정보를 새로 입력해 주세요.
          </div>
        )}

        {plan.cutStepIds.length > 0 && <CutBanner cutStepIds={plan.cutStepIds} />}

        <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
          <div className="overflow-x-auto rounded-[22px] border border-[#eee] bg-[linear-gradient(#FFFDFB,#FFF9F3)]">
            <MapCanvas
              steps={orderedSteps.map((step) => ({
                id: step.id,
                title: allStepTitles.get(step.id) ?? step.title,
                mode: step.mode,
              }))}
              completedStepIds={completedStepIds}
              currentStep={currentStepId}
              weakSteps={weakSteps}
              goalReached={goalReached}
              rewardAfter={rewardAfter}
              onNavigateStep={(stepId) => router.push(`/study/${stepId}`)}
            />
          </div>

          <aside className="grid gap-4">
            <div className="rounded-[18px] border border-[#eee] p-[26px]">
              <div className="mb-3 flex items-baseline justify-between"><span className="text-[13.5px] font-bold">전체 진행률</span><span className="font-jua text-[22px] text-[#FF7A00]">{percent}%</span></div>
              <div className="h-2.5 overflow-hidden rounded-full bg-[#FFF3E8]"><div className="h-full rounded-full bg-[#FF7A00] transition-[width] duration-300" style={{ width: `${percent}%` }} /></div>
              <div className="mt-2.5 text-[12.5px] text-[#888]">
                STEP {completedCount} / {totalSteps} 완료 ·{" "}
                {remainingMinutes > 0 ? `남은 예상 ${formatMinutes(remainingMinutes)}` : "모든 STEP 완료!"}
              </div>
            </div>
            <div className="rounded-[18px] border border-[#eee] p-[26px]">
              <div className="mb-4 text-[13.5px] font-bold">보상</div>
              <div className="mb-3.5 flex items-center gap-3"><div className="flex size-11 items-center justify-center rounded-xl bg-[#FFF3E8]">{/* eslint-disable-next-line @next/next/no-img-element */}<img src="/reward/coin.png" alt="" width={30} height={30} className="object-contain" /></div><div><div className="text-[15px] font-bold">코인 {openedRewardCount}개</div><div className="text-[12.5px] text-[#888]">상자 {rewardAfter.length}개 중 {openedRewardCount}개 개봉</div></div></div>
              <div className="flex gap-2">
                {rewardAfter.map((id, index) => (
                  <div key={id} className={`h-1.5 flex-1 rounded-full ${index < openedRewardCount ? "bg-[#FF7A00]" : "bg-[#eee]"}`} />
                ))}
              </div>
            </div>
            <div className="rounded-[18px] border border-amber-200 bg-amber-50/40 p-[26px]">
              <div className="mb-3.5 flex items-center justify-between">
                <span className="flex items-center gap-1.5 text-[13.5px] font-bold text-amber-800"><svg width="11" height="13" viewBox="0 0 24 28" fill="none" aria-hidden="true"><path d="M5 2h14a1 1 0 0 1 1 1v23l-8-5.5L4 26V3a1 1 0 0 1 1-1Z" fill="#F59F00" stroke="#E8590C" strokeWidth="1.6" strokeLinejoin="round" /></svg>다시 볼 개념</span>
                <span className="text-xs text-amber-700">{weakSteps.length}개</span>
              </div>
              {weakSteps.length ? (
                <div className="grid gap-2">
                  {weakSteps.map((id) => {
                    const title = allStepTitles.get(id);
                    if (!title) return null;
                    const step = { id, title };
                    return (
                      <button
                        key={step.id}
                        type="button"
                        onClick={() => router.push(`/study/${step.id}`)}
                        className="flex cursor-pointer items-center gap-2 rounded-xl border border-amber-200 bg-white px-3 py-2.5 text-left text-[13px] text-amber-900 transition-colors hover:border-amber-400"
                      >
                        <span className="font-bold">STEP {step.id}</span>
                        <span className="min-w-0 flex-1 truncate">{step.title}</span>
                        <span aria-hidden="true">›</span>
                      </button>
                    );
                  })}
                </div>
              ) : (
                <p className="text-[12.5px] leading-5 text-[#888]">아직 표시한 개념이 없어요</p>
              )}
            </div>
            <div className="rounded-[18px] border border-[#eee] p-[26px]">
              <div className="mb-3.5 flex items-baseline justify-between">
                <span className="text-[13.5px] font-bold">STEP 목록</span>
                <span className="text-[12px] text-[#888]">{prepState === "skimmed" ? "핵심 우선 정렬" : prepState === "review" ? "퀴즈 우선" : "기본 순서"}</span>
              </div>
              <div className="grid gap-[11px]">
                {plan.steps.map((step) => {
                  const isDone = completedStepIds.includes(step.id);
                  const isCurrent = step.id === currentStepId;
                  return (
                    <div key={step.id} className={`flex items-center gap-2.5 text-[13.5px] ${isCurrent ? "font-bold text-[#E85D00]" : "text-[#888]"}`}>
                      {isDone ? (
                        <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-[#FFE0C4]"><CheckMini size={10} /></span>
                      ) : isCurrent ? (
                        <span className="size-5 shrink-0 rounded-full bg-[#FF7A00]" />
                      ) : (
                        <span className="size-5 shrink-0 rounded-full border-2 border-[#eee]" />
                      )}
                      <span className="flex-1 truncate">{step.title}</span>
                      <span className="flex shrink-0 items-center gap-1.5">
                        {step.quizFirst && <span className="rounded-full bg-[#FFF3E8] px-1.5 py-0.5 text-[10.5px] font-bold text-[#E85D00]">퀴즈</span>}
                        {step.mode === "skim" && <span className="rounded-full bg-[#F4F4F4] px-1.5 py-0.5 text-[10.5px] text-[#888]">{MODE_LABEL.skim}</span>}
                        <span className="tabular-nums text-[12px] text-[#aaa]">{step.minutes}분</span>
                      </span>
                    </div>
                  );
                })}
              </div>

              {cutSteps.length > 0 && (
                <div className="mt-3.5 border-t border-[#eee] pt-3.5">
                  <button
                    type="button"
                    onClick={() => setShowCut((value) => !value)}
                    aria-expanded={showCut}
                    className="flex w-full items-center justify-between text-[12.5px] font-bold text-[#888] transition-colors hover:text-[#E85D00]"
                  >
                    <span>제외된 STEP {cutSteps.length}개 보기</span>
                    <span className={`transition-transform duration-300 ${showCut ? "rotate-180" : ""}`} aria-hidden="true">⌄</span>
                  </button>
                  <div className={`grid gap-2 overflow-hidden transition-all duration-500 ${showCut ? "mt-3 max-h-96 opacity-100" : "max-h-0 opacity-0"}`}>
                    {cutSteps.map((step) => (
                      <div key={step.id} className="flex items-center gap-2.5 text-[13.5px] opacity-40">
                        <span className="size-5 shrink-0 rounded-full border-2 border-[#eee]" />
                        <span className="flex-1 truncate line-through">{step.title}</span>
                        <span className="shrink-0 rounded-full bg-[#FFF3E8] px-1.5 py-0.5 text-[10.5px] font-bold text-[#E85D00]">시간 부족</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </aside>
        </div>
      </main>
    </div>
  );
}

function CutBanner({ cutStepIds }: { cutStepIds: number[] }) {
  const [shown, setShown] = useState(false);
  useEffect(() => {
    const raf = requestAnimationFrame(() => setShown(true));
    return () => cancelAnimationFrame(raf);
  }, []);

  return (
    <div
      className={`mb-6 flex items-center gap-2.5 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-[13.5px] font-medium text-amber-700 transition-all duration-[400ms] ease-out ${
        shown ? "translate-y-0 opacity-100" : "-translate-y-2 opacity-0"
      }`}
    >
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true" className="shrink-0"><circle cx="12" cy="13" r="8.5" stroke="#B45309" strokeWidth="2" /><path d="M12 8.5V13l3 2M5 3 2.5 5.5M19 3l2.5 2.5" stroke="#B45309" strokeWidth="2" strokeLinecap="round" /></svg>
      <span>시간이 부족해 STEP {cutStepIds.join("·")}은(는) 제외했어요.</span>
    </div>
  );
}

type MapStep = { id: number; title: string; mode: CurriculumStep["mode"] };

type MapCanvasProps = {
  /** 살아있는 STEP 을 진행 순서대로. 좌표는 여기서 경로를 따라 계산한다. */
  steps: MapStep[];
  completedStepIds: number[];
  currentStep: number;
  weakSteps: number[];
  goalReached: boolean;
  /** 이 STEP 을 완료하면 열리는 상자 목록 */
  rewardAfter: number[];
  onNavigateStep: (stepId: number) => void;
};

/** STEP 이 경로 위에서 차지하는 진행 비율. 시작 여백을 두고 0.9 까지 고르게 편다. */
function stepProgressAt(index: number, count: number): number {
  if (count <= 1) return 0.06;
  return 0.04 + (index / (count - 1)) * 0.86;
}

/**
 * 벼락치기 맵.
 *
 * <p>STEP 노드를 고정 좌표가 아니라 <b>경로 위 진행 비율</b>로 놓는다.
 * 계획마다 STEP 수가 다르기 때문이다 — 5개든 9개든 같은 길 위에 고르게 늘어선다.
 * 좌표는 마운트 후 경로 길이를 재서 계산한다(SSR 에서는 getPointAtLength 를 쓸 수 없다).
 */
function MapCanvas({ steps, completedStepIds, currentStep, weakSteps, goalReached, rewardAfter, onNavigateStep }: MapCanvasProps) {
  const maskId = useId();
  const roadGradientId = useId();
  const fullPathRef = useRef<SVGPathElement>(null);
  const [length, setLength] = useState(0);
  const [hasMeasured, setHasMeasured] = useState(false);

  useEffect(() => {
    const el = fullPathRef.current;
    if (!el) return;
    setLength(el.getTotalLength());
    setHasMeasured(true);
  }, []);

  // STEP·상자·시험장 좌표를 경로에서 뽑는다. 경로 길이가 잡힌 뒤에만 그린다.
  const layout = (() => {
    const el = fullPathRef.current;
    if (!el || length <= 0) return null;
    const at = (p: number) => el.getPointAtLength(length * Math.min(Math.max(p, 0), 1));

    const stepNodes = steps.map((step, index) => {
      const p = stepProgressAt(index, steps.length);
      const point = at(p);
      return { ...step, index, p, x: point.x, y: point.y };
    });
    const progressOf = new Map(stepNodes.map((node) => [node.id, node.p]));

    const rewardNodes = rewardAfter
      .map((afterId) => {
        const index = stepNodes.findIndex((node) => node.id === afterId);
        if (index < 0 || index >= stepNodes.length - 1) return null;
        const p = (stepNodes[index].p + stepNodes[index + 1].p) / 2;
        const point = at(p);
        return { afterId, x: point.x, y: point.y };
      })
      .filter((node): node is { afterId: number; x: number; y: number } => node !== null);

    const goalPoint = at(1);
    return { stepNodes, progressOf, rewardNodes, goalPoint };
  })();

  // 길 채움(완주 표시)은 마지막으로 완료한 STEP 까지다.
  let progress = 0.02;
  if (layout) {
    for (const step of steps) {
      if (completedStepIds.includes(step.id)) {
        progress = layout.progressOf.get(step.id) ?? progress;
      } else {
        break;
      }
    }
  }
  if (goalReached) progress = 1;

  // 캐릭터는 완료 지점이 아니라 "지금 할 STEP" 바로 위에 선다. 다음에 무엇을 하면
  // 되는지가 완료 이력보다 먼저 보여야 하기 때문이다. 전부 끝나면 시험장으로 간다.
  const characterProgress = goalReached
    ? 1
    : (layout?.progressOf.get(currentStep) ?? progress);

  const character = (() => {
    const el = fullPathRef.current;
    if (!el || length <= 0) return null;
    const pt = el.getPointAtLength(length * characterProgress);
    return { x: pt.x, y: pt.y };
  })();

  const dashOffset = length > 0 ? length * (1 - progress) : 0;

  // 캐릭터 앞쪽(다음 목적지까지)만 옅은 점선으로 "앞으로 갈 길"을 보여준다.
  const nextProgress = (() => {
    if (!layout || goalReached) return null;
    const candidates = layout.stepNodes.map((node) => node.p);
    candidates.push(1);
    return candidates.find((value) => value > progress + 1e-6) ?? null;
  })();
  const aheadSegment =
    length > 0 && nextProgress !== null && nextProgress > progress
      ? { dash: (nextProgress - progress) * length, offset: -(progress * length) }
      : null;

  const characterMessage = goalReached
    ? "시험장 도착!"
    : completedStepIds.length === 0
      ? "여기서부터 시작!"
      : `STEP ${currentStep} 진행 중`;

  return (
    <div className="relative" style={{ width: VIEW_W, height: VIEW_H }}>
      <svg width={VIEW_W} height={VIEW_H} viewBox={`0 ${-PAD_TOP} ${VIEW_W} ${VIEW_H}`} className="absolute inset-0" aria-hidden="true">
        <defs>
          {/* 길 표면의 세로 그라데이션 — 위가 밝고 아래가 짙은 찰흙 질감의 바탕 */}
          <linearGradient id={roadGradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#FFE7CC" />
            <stop offset="55%" stopColor="#FFD9B4" />
            <stop offset="100%" stopColor="#F8C48D" />
          </linearGradient>
          <mask id={maskId} maskUnits="userSpaceOnUse" x={0} y={-PAD_TOP} width={VIEW_W} height={VIEW_H}>
            <rect x={0} y={-PAD_TOP} width={VIEW_W} height={VIEW_H} fill="black" />
            {length > 0 && (
              <path
                d={TRACK_PATH_D}
                stroke="white"
                strokeWidth="26"
                fill="none"
                strokeLinecap="round"
                strokeDasharray={length}
                strokeDashoffset={dashOffset}
                style={{ transition: hasMeasured ? "stroke-dashoffset 0.8s ease-out" : "none" }}
              />
            )}
          </mask>
        </defs>

        {/*
          클레이모피즘 길 — 4겹으로 찰흙 튜브를 흉내낸다.
          1) 바닥 그림자: 살짝 아래로 밀린 짙은 주황  2) 몸통: 그라데이션
          3) 윗면 하이라이트: 좁고 밝은 선  4) 진행/점선 오버레이(기존 유지)
        */}
        <g transform="translate(0,6)">
          <path d={TRACK_PATH_D} stroke="rgba(214,112,18,0.30)" strokeWidth={32} fill="none" strokeLinecap="round" />
        </g>
        <path ref={fullPathRef} d={TRACK_PATH_D} stroke={`url(#${roadGradientId})`} strokeWidth={30} fill="none" strokeLinecap="round" />
        <g transform="translate(0,-7)">
          <path d={TRACK_PATH_D} stroke="rgba(255,255,255,0.65)" strokeWidth={7} fill="none" strokeLinecap="round" />
        </g>
        {length > 0 && (
          <path
            d={TRACK_PATH_D}
            stroke="#FF7A00"
            strokeOpacity={0.26}
            strokeWidth={30}
            fill="none"
            strokeLinecap="round"
            strokeDasharray={length}
            strokeDashoffset={dashOffset}
            style={{ transition: hasMeasured ? "stroke-dashoffset 0.8s ease-out" : "none" }}
          />
        )}
        {length > 0 && (
          <path
            d={TRACK_PATH_D}
            stroke="#FF7A00"
            strokeWidth={5}
            fill="none"
            strokeLinecap="round"
            strokeDasharray="10 12"
            className="animate-track-flow"
            mask={`url(#${maskId})`}
          />
        )}
        {aheadSegment && (
          <path
            d={TRACK_PATH_D}
            stroke="#FF7A00"
            strokeWidth="6"
            fill="none"
            strokeLinecap="round"
            strokeDasharray={`${aheadSegment.dash} ${length}`}
            strokeDashoffset={aheadSegment.offset}
            opacity={0.5}
          />
        )}
      </svg>

      {layout?.stepNodes.map((node) => (
        <StepNode
          key={node.id}
          stepId={node.id}
          title={node.title}
          mode={node.mode}
          x={node.x}
          y={node.y}
          labelAbove={node.index % 2 === 1}
          isDone={completedStepIds.includes(node.id)}
          isCurrent={node.id === currentStep && !goalReached}
          isLocked={node.id > currentStep}
          isWeak={weakSteps.includes(node.id)}
          onNavigate={() => onNavigateStep(node.id)}
        />
      ))}

      {layout?.rewardNodes.map((node) => {
        const opened = completedStepIds.includes(node.afterId);
        return (
          <div
            key={`reward-${node.afterId}`}
            className="absolute z-0"
            style={{ left: node.x - 30, top: node.y - 38 + PAD_TOP }}
            title={opened ? "상자를 열었어요" : "다음 STEP 을 완료하면 열려요"}
          >
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={opened ? "/reward/box-open.png" : "/reward/box-closed.png"}
              alt=""
              width={60}
              height={54}
              draggable={false}
              className={`select-none object-contain drop-shadow-[0_6px_10px_rgba(180,90,10,.25)] ${opened ? "" : "opacity-85"}`}
            />
          </div>
        );
      })}

      {layout && (
        <div className="absolute z-10 w-[104px] text-center" style={{ left: layout.goalPoint.x - 52, top: layout.goalPoint.y - 46 + PAD_TOP }}>
          <div className="clay-circle mx-auto flex size-20 items-center justify-center rounded-[24px]">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M6 21V4" stroke="#E85D00" strokeWidth="2.4" strokeLinecap="round" />
              <path d="M6 4h11l-2.5 3.5L17 11H6" fill="#FF7A00" stroke="#E85D00" strokeWidth="1.6" strokeLinejoin="round" />
            </svg>
          </div>
          <div className="font-jua mt-[9px] text-sm text-[#E85D00]">시험장</div>
        </div>
      )}

      {character && (
        <div
          className="absolute z-30"
          style={{
            left: character.x,
            top: character.y + PAD_TOP,
            transform: "translate(-50%, -50%)",
            transition: hasMeasured ? "left 0.8s ease-out, top 0.8s ease-out" : "none",
          }}
        >
          <div className="relative flex flex-col items-center gap-1" style={{ transform: "translateY(-96px)" }}>
            <div className="font-jua whitespace-nowrap rounded-[12px_12px_12px_3px] border border-[#FFE0C4] bg-white px-[13px] py-2 text-[13.5px] shadow-[0_4px_12px_rgba(0,0,0,.07)]">
              {characterMessage}
            </div>
            <Ghost width={78} ripple={false} mood={goalReached ? "happy" : "default"} className="animate-bob drop-shadow-[0_8px_12px_rgba(255,122,0,.3)]" />
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * 경로 위 STEP 동그라미. 클레이모피즘 질감이고, 숫자는 원의 정중앙에 놓는다.
 */
function StepNode({
  stepId,
  title,
  mode,
  x,
  y,
  labelAbove,
  isDone,
  isCurrent,
  isLocked,
  isWeak,
  onNavigate,
}: {
  stepId: number;
  title: string;
  mode: CurriculumStep["mode"];
  x: number;
  y: number;
  labelAbove: boolean;
  isDone: boolean;
  isCurrent: boolean;
  isLocked: boolean;
  isWeak: boolean;
  onNavigate: () => void;
}) {
  // 지금 할 STEP 만 크게. 나머지는 길 위의 이정표 정도로 작게 둔다.
  const size = isCurrent ? 84 : isDone ? 52 : 48;
  const left = x - size / 2;
  const top = y - size / 2 + PAD_TOP;
  // 라벨은 위·아래를 번갈아 놓아 촘촘한 구간에서 서로 겹치지 않게 한다.
  const labelTop = labelAbove ? y - size / 2 - 56 + PAD_TOP : y + size / 2 + 8 + PAD_TOP;

  const weakRing = isWeak ? "ring-2 ring-amber-400 ring-offset-2" : "";
  const numberClass = "font-jua flex size-full items-center justify-center leading-none";

  return (
    <div className="group">
      {isCurrent ? (
        <div className="absolute" style={{ left, top, width: size, height: size }}>
          <div className="absolute inset-[-10px] animate-pulse-ring rounded-full bg-[#FF7A00]" />
          <button
            type="button"
            onClick={onNavigate}
            className={`clay-circle-active absolute inset-0 cursor-pointer rounded-full text-[26px] text-white ${weakRing}`}
          >
            <span className={numberClass}>{stepId}</span>
          </button>
        </div>
      ) : (
        <button
          type="button"
          disabled={isLocked}
          onClick={() => !isLocked && onNavigate()}
          style={{ left, top, width: size, height: size }}
          className={`absolute rounded-full ${weakRing} ${
            isDone
              ? "clay-done cursor-pointer"
              : `clay-circle text-[17px] ${isLocked ? "cursor-default text-[#b9b9b9]" : "cursor-pointer text-[#8a6a4d]"}`
          }`}
        >
          {isDone ? (
            <span className="flex size-full items-center justify-center"><CheckIcon /></span>
          ) : (
            <span className={numberClass}>{stepId}</span>
          )}
        </button>
      )}

      {/* 라벨은 평소에 숨긴다. 길에는 숫자와 상자만 보이고, 자세한 제목은 올려다볼 때만. */}
      <div
        className={`pointer-events-none absolute z-20 w-[150px] text-center text-[12.5px] opacity-0 transition-opacity duration-150 group-hover:opacity-100 ${
          isCurrent ? "font-bold text-[#E85D00]" : isDone ? "font-bold text-[#888]" : "text-[#888]"
        }`}
        style={{ left: x, top: labelTop, transform: "translate(-50%, 0)" }}
      >
        <span className={`inline-block rounded-full px-3 py-[5px] text-[12px] font-bold ${isCurrent ? "bg-[#FF7A00] text-white" : "bg-white/85 text-[#888]"}`}>
          STEP {stepId}
        </span>
        <div className="mt-[5px] flex items-center justify-center gap-1 font-medium text-[#222]">
          <span className="max-w-[140px] truncate rounded-md bg-white/85 px-1.5 py-0.5">{title}</span>
          {mode === "skim" && <span className="rounded-full bg-[#F4F4F4] px-1.5 py-0.5 text-[10px] text-[#888]">훑기</span>}
          {mode === "review" && <span className="rounded-full bg-[#FFF3E8] px-1.5 py-0.5 text-[10px] font-bold text-[#E85D00]">퀴즈</span>}
        </div>
      </div>
    </div>
  );
}
