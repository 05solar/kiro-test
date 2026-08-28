"use client";

import { useEffect, useId, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { AppHeader, CheckIcon, Ghost, PrimaryButton } from "@/app/_components/ui";
import { useExamStore, usePlanStore } from "@/app/_components/store";
import { useCurriculum } from "@/app/_components/use-curriculum";
import { useHydrated } from "@/app/_components/use-hydrated";
import {
  formatMinutes,
  type CurriculumResult,
  type CurriculumStep,
  type PrepState,
} from "@/lib/curriculum";
import {
  DEFAULT_NODE_LABEL_OFFSET,
  GOAL_PROGRESS,
  MAP_NODES,
  NODE_LABEL_OFFSET,
  NODE_PROGRESS,
  TRACK_PATH_D,
  type MapNode,
} from "@/lib/mapNodes";

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

const STEP_IDS = [1, 2, 3, 4, 5, 6, 7];

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

  const cutStepSet = new Set(plan.cutStepIds);
  const totalSteps = plan.steps.length;
  const completedCount = plan.steps.filter((step) => completedStepIds.includes(step.id)).length;
  const percent = totalSteps > 0 ? Math.round((completedCount / totalSteps) * 100) : 0;

  // GOAL 도달: cut 반영된 plan의 모든 STEP이 완료됐을 때만 true (상수 사용 안 함).
  const goalReached = totalSteps > 0 && orderedSteps.every((step) => completedStepIds.includes(step.id));

  // 살아있는(=cut되지 않은) 상자만 카운트. 개봉 여부는 afterStepId STEP 완료로 판정.
  const activeRewards = MAP_NODES.filter(
    (node) => node.kind === "reward" && !cutStepSet.has(node.afterStepId)
  );
  const openedRewardCount = activeRewards.filter(
    (node) => node.kind === "reward" && completedStepIds.includes(node.afterStepId)
  ).length;

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

  const stepMeta = new Map<number, CurriculumStep>(plan.steps.map((step) => [step.id, step]));

  // 경로는 고정이므로 진행 비율(p)은 좌표가 아니라 상수 NODE_PROGRESS 표에서 구한다.
  // "마지막 완료 스텝"과 "다음 살아있는 스텝"은 반드시 orderedSteps(STEP 번호 순서) 기준으로만
  // 판단한다. plan.steps는 skimmed 재배치로 순서가 달라져 진행 비율이 뒤틀릴 수 있다.
  let progress = 0;
  for (const step of orderedSteps) {
    if (completedStepIds.includes(step.id)) {
      progress = NODE_PROGRESS[step.id];
    } else {
      break;
    }
  }
  if (goalReached) progress = GOAL_PROGRESS;

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
            <p className="text-[15px] text-[#888]">STEP {totalSteps}개 · 상자 {activeRewards.length}개</p>
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
              {firstWeak ? "다시 볼 개념 복습하기" : "학습 완료 🎉"}
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
              progress={progress}
              cutStepSet={cutStepSet}
              completedStepIds={completedStepIds}
              currentStep={currentStepId}
              weakSteps={weakSteps}
              goalReached={goalReached}
              stepMeta={stepMeta}
              onNavigateStep={(stepId) => router.push(`/study/${stepId}`)}
            />
          </div>

          <aside className="grid gap-4">
            <div className="rounded-[18px] border border-[#eee] p-[26px]">
              <div className="mb-3 flex items-baseline justify-between"><span className="text-[13.5px] font-bold">전체 진행률</span><span className="font-jua text-[22px] text-[#FF7A00]">{percent}%</span></div>
              <div className="h-2.5 overflow-hidden rounded-full bg-[#FFF3E8]"><div className="h-full rounded-full bg-[#FF7A00] transition-[width] duration-300" style={{ width: `${percent}%` }} /></div>
              <div className="mt-2.5 text-[12.5px] text-[#888]">
                STEP {completedCount} / {totalSteps} 완료 ·{" "}
                {remainingMinutes > 0 ? `남은 예상 ${formatMinutes(remainingMinutes)}` : "모든 STEP 완료 🎉"}
              </div>
            </div>
            <div className="rounded-[18px] border border-[#eee] p-[26px]">
              <div className="mb-4 text-[13.5px] font-bold">보상</div>
              <div className="mb-3.5 flex items-center gap-3"><div className="flex size-11 items-center justify-center rounded-xl bg-[#FFF3E8] text-xl">🍌</div><div><div className="text-[15px] font-bold">바나나 {openedRewardCount}개</div><div className="text-[12.5px] text-[#888]">상자 {activeRewards.length}개 중 {openedRewardCount}개 개봉</div></div></div>
              <div className="flex gap-2">
                {activeRewards.map((node, index) => (
                  <div key={node.id} className={`h-1.5 flex-1 rounded-full ${index < openedRewardCount ? "bg-[#FF7A00]" : "bg-[#eee]"}`} />
                ))}
              </div>
            </div>
            <div className="rounded-[18px] border border-amber-200 bg-amber-50/40 p-[26px]">
              <div className="mb-3.5 flex items-center justify-between">
                <span className="text-[13.5px] font-bold text-amber-800">🔖 다시 볼 개념</span>
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
                        <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-[#FFE0C4] text-[11px] text-[#E85D00]">✓</span>
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
      <span aria-hidden="true">⏰</span>
      <span>시간이 부족해 STEP {cutStepIds.join("·")}은(는) 제외했어요.</span>
    </div>
  );
}

type MapCanvasProps = {
  progress: number;
  cutStepSet: Set<number>;
  completedStepIds: number[];
  currentStep: number;
  weakSteps: number[];
  goalReached: boolean;
  stepMeta: Map<number, CurriculumStep>;
  onNavigateStep: (stepId: number) => void;
};

function MapCanvas({ progress, cutStepSet, completedStepIds, currentStep, weakSteps, goalReached, stepMeta, onNavigateStep }: MapCanvasProps) {
  const maskId = useId();
  const fullPathRef = useRef<SVGPathElement>(null);
  const [length, setLength] = useState(0);
  const [character, setCharacter] = useState<{ x: number; y: number } | null>(null);
  const [hasMeasured, setHasMeasured] = useState(false);

  // TRACK_PATH_D는 고정 상수이므로 길이도 마운트 시 한 번만 구하면 된다.
  // getTotalLength/getPointAtLength는 SSR에서 동작하지 않으므로 effect 내부에서만 호출한다.
  useEffect(() => {
    const el = fullPathRef.current;
    if (!el) return;
    setLength(el.getTotalLength());
    setHasMeasured(true);
  }, []);

  // 캐릭터 좌표는 항상 fullPathRef 기준 getPointAtLength(L*progress)로만 계산한다.
  useEffect(() => {
    const el = fullPathRef.current;
    if (!el || length <= 0) return;
    const pt = el.getPointAtLength(length * progress);
    setCharacter({ x: pt.x, y: pt.y });
  }, [length, progress]);

  const dashOffset = length > 0 ? length * (1 - progress) : 0;

  // 캐릭터 앞쪽(다음 목적지까지)만 옅은 점선으로 "앞으로 갈 길"을 보여준다.
  const nextProgress = (() => {
    if (goalReached) return null;
    const candidates = STEP_IDS.filter((id) => !cutStepSet.has(id)).map((id) => NODE_PROGRESS[id]);
    candidates.push(GOAL_PROGRESS);
    const next = candidates.find((value) => value > progress + 1e-6);
    return next ?? null;
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
          {/* 완주 구간(흰색)만 보이게 하는 마스크. strokeDasharray는 여기서만
              "진행 연장" 용도로 쓰고, 점선 레이어(3층)의 dasharray와는 절대 겹치지 않는다. */}
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

        {/* 1층 - 배경 트랙: 항상 전체 표시 */}
        <path ref={fullPathRef} d={TRACK_PATH_D} stroke="#FFE0C4" strokeWidth={26} fill="none" strokeLinecap="round" />
        {/* 2층 - 완주 채움: 옅은 오렌지, dashoffset으로 진행분만 노출(진행 연장 애니메이션의 핵심) */}
        {length > 0 && (
          <path
            d={TRACK_PATH_D}
            stroke="#FF7A00"
            strokeOpacity={0.22}
            strokeWidth={26}
            fill="none"
            strokeLinecap="round"
            strokeDasharray={length}
            strokeDashoffset={dashOffset}
            style={{ transition: hasMeasured ? "stroke-dashoffset 0.8s ease-out" : "none" }}
          />
        )}
        {/* 3층 - 완주 점선: 마스크로 완주 구간에만 노출, dasharray는 점선 패턴 전용(흐르는 애니메이션) */}
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
        {/* 캐릭터 앞쪽 다음 목적지까지 옅은 점선으로 "갈 길"을 표시 */}
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

      {MAP_NODES.map((node) => {
        if (node.kind === "step") {
          const isCut = cutStepSet.has(node.stepId);
          return (
            <StepNode
              key={node.id}
              node={node}
              isCut={isCut}
              isDone={completedStepIds.includes(node.stepId)}
              // 전부 완료(p===1)면 캐릭터가 GOAL에 있으므로, 마지막 STEP도
              // 더 이상 "지금 여기" 펄스/pill이 아니라 일반 완료 노드로만 표시한다.
              isCurrent={node.stepId === currentStep && !goalReached}
              isLocked={node.stepId > currentStep}
              isWeak={weakSteps.includes(node.stepId)}
              mode={stepMeta.get(node.stepId)?.mode ?? "full"}
              onNavigate={() => onNavigateStep(node.stepId)}
            />
          );
        }
        if (node.kind === "reward") {
          const rewardCut = cutStepSet.has(node.afterStepId);
          const opened = !rewardCut && completedStepIds.includes(node.afterStepId);
          if (rewardCut) return null; // afterStep이 cut되면 상자도 숨긴다.
          return (
            <div
              key={node.id}
              // 노드 라벨(z-10)보다 낮게 두어 바나나가 라벨을 가리지 않게 한다.
              className={`absolute z-0 flex size-12 items-center justify-center rounded-xl border-2 text-lg ${
                opened ? "border-[#FFE0C4] bg-white" : "border-dashed border-[#FFE0C4] bg-[#FFF3E8] opacity-75"
              }`}
              style={{ left: node.x - 24, top: node.y - 24 + PAD_TOP }}
            >
              {opened ? "🍌" : "📦"}
            </div>
          );
        }
        // goal — 원본 위치(left-742 top-52) 그대로 복원
        return (
          <div key={node.id} className="absolute w-[104px] text-center" style={{ left: 742, top: 52 + PAD_TOP }}>
            <div className="mx-auto flex size-20 items-center justify-center rounded-[20px] border-2 border-[#FFE0C4] bg-white text-[30px]">🏁</div>
            <div className="font-jua mt-[9px] text-sm text-[#E85D00]">오전 9시<br />시험장</div>
          </div>
        );
      })}

      {/* 캐릭터 — 경로 위 단일 말풍선 */}
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
          <div className="relative flex flex-col items-center gap-1.5" style={{ transform: "translateY(-58px)" }}>
            <div className="font-jua whitespace-nowrap rounded-[12px_12px_12px_3px] border border-[#FFE0C4] bg-white px-[13px] py-2 text-[13.5px] shadow-[0_4px_12px_rgba(0,0,0,.07)]">
              {characterMessage}
            </div>
            <Ghost width={62} mood={goalReached ? "excited" : "eyes"} className="animate-bob drop-shadow-[0_8px_12px_rgba(255,122,0,.3)]" />
          </div>
        </div>
      )}
    </div>
  );
}

function StepNode({
  node,
  isCut,
  isDone,
  isCurrent,
  isLocked,
  isWeak,
  mode,
  onNavigate,
}: {
  node: Extract<MapNode, { kind: "step" }>;
  isCut: boolean;
  isDone: boolean;
  isCurrent: boolean;
  isLocked: boolean;
  isWeak: boolean;
  mode: CurriculumStep["mode"];
  onNavigate: () => void;
}) {
  const size = isCurrent ? 88 : isDone ? 72 : 68;
  const left = node.x - size / 2;
  const top = node.y - size / 2 + PAD_TOP;

  // STEP별 라벨 offset. node.x/node.y 좌표 기준 절대값이라 노드 크기(size)와 무관하다.
  // isCurrent일 때 노드 지름이 88px까지 커지므로, size에 상대적인 위치를 쓰면
  // 노드가 라벨(특히 원 안의 숫자)을 가리는 회귀가 재발한다.
  const offset = NODE_LABEL_OFFSET[node.stepId] ?? DEFAULT_NODE_LABEL_OFFSET;
  const labelCenterX = node.x + offset.dx;
  const labelTop = node.y + offset.dy + PAD_TOP;

  return (
    <div className={`transition-all duration-500 ${isCut ? "pointer-events-none opacity-25 grayscale" : ""}`}>
      {isCurrent ? (
        <div className="absolute" style={{ left, top, width: size, height: size }}>
          <div className="absolute inset-[-10px] animate-pulse-ring rounded-full bg-[#FF7A00]" />
          <button
            type="button"
            className={`font-jua absolute inset-0 flex cursor-pointer items-center justify-center rounded-full border-4 bg-[#FF7A00] text-[26px] text-white shadow-[0_10px_24px_rgba(255,122,0,.4)] ${isWeak ? "border-amber-400" : "border-white"}`}
            onClick={onNavigate}
          >
            {node.stepId}
          </button>
        </div>
      ) : (
        <button
          type="button"
          disabled={isLocked || isCut}
          onClick={() => !isLocked && !isCut && onNavigate()}
          style={{ left, top, width: size, height: size }}
          className={`absolute flex items-center justify-center rounded-full border-[3px] shadow-[0_4px_12px_rgba(0,0,0,.06)] ${
            isDone
              ? `cursor-pointer bg-[#FFE0C4] ${isWeak ? "border-amber-400" : "border-white"}`
              : `font-jua cursor-default border-2 bg-white text-[21px] text-[#888] ${isWeak ? "border-amber-400" : "border-[#eee]"}`
          }`}
        >
          {isDone ? <CheckIcon /> : node.stepId}
        </button>
      )}

      {/*
        라벨: SVG 경로(z-index 없는 기본 stacking, 배경)보다 위, 상자(z-0)보다 위,
        말풍선(z-30)보다는 아래인 z-20에 둔다. 반투명 흰 배경 칩으로 선 위에서도 읽히게 한다.
      */}
      <div
        className={`absolute z-20 w-[150px] text-center text-[12.5px] ${
          isCurrent ? "font-bold text-[#E85D00]" : isDone ? "font-bold text-[#888]" : "text-[#888]"
        }`}
        style={{ left: labelCenterX, top: labelTop, transform: "translate(-50%, 0)" }}
      >
        {isCurrent ? (
          <>
            <span className="inline-block rounded-full bg-[#FF7A00] px-3 py-[5px] text-[12.5px] font-bold text-white">STEP {node.stepId}</span>
            <div className="mt-[5px] flex items-center justify-center gap-1 font-medium text-[#222]">
              <span className="rounded-md bg-white/85 px-1.5 py-0.5">{node.label}</span>
              {mode === "skim" && <span className="rounded-full bg-[#F4F4F4] px-1.5 py-0.5 text-[10px] text-[#888]">훑기</span>}
              {mode === "review" && <span className="rounded-full bg-[#FFF3E8] px-1.5 py-0.5 text-[10px] font-bold text-[#E85D00]">퀴즈</span>}
            </div>
          </>
        ) : (
          <>
            <span className="rounded-md bg-white/85 px-1.5 py-0.5">STEP {node.stepId}</span>
            <br />
            <span className="mt-1 inline-flex items-center gap-1 font-normal">
              <span className="rounded-md bg-white/85 px-1.5 py-0.5">{node.label}</span>
              {mode === "skim" && <span className="rounded-full bg-[#F4F4F4] px-1.5 py-0.5 text-[10px] text-[#888]">훑기</span>}
              {mode === "review" && <span className="rounded-full bg-[#FFF3E8] px-1.5 py-0.5 text-[10px] font-bold text-[#E85D00]">퀴즈</span>}
            </span>
          </>
        )}
      </div>
    </div>
  );
}
