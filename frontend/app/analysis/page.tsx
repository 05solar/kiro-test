"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { FlowSteps } from "@/app/_components/flow-steps";
import { AppHeader, Ghost, PrimaryButton, SecondaryButton } from "@/app/_components/ui";
import { useSessionStore } from "@/app/_components/session-store";
import { useHydrated } from "@/app/_components/use-hydrated";
import { createCurriculum, runAnalysis, toMessage } from "@/lib/api";

/**
 * 실제로 일어나는 일과 같은 순서로 적는다.
 *
 * 분석과 계획 생성은 서버에서 두 번의 요청으로 나뉜다. 화면도 그 경계를 그대로 보여준다.
 * 예전에는 4개 항목이 타이머로 차례차례 켜졌는데, 그건 서버가 무엇을 하는지와
 * 아무 관계가 없었다.
 */
const TASKS = [
  { key: "analyze", label: "자료를 읽고 학습 주제 뽑기" },
  { key: "plan", label: "남은 시간에 맞춰 STEP 배분하기" },
] as const;

type Phase = "analyzing" | "planning" | "done" | "failed";

export default function AnalysisPage() {
  const router = useRouter();
  const hydrated = useHydrated();
  const sessionCode = useSessionStore((state) => state.sessionCode);

  const [phase, setPhase] = useState<Phase>("analyzing");
  const [topicCount, setTopicCount] = useState<number | null>(null);
  const [stepCount, setStepCount] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  // React 18+ 개발 모드는 effect 를 두 번 실행한다. AI 호출은 과금되므로
  // 같은 세션에서 두 번 부르지 않도록 막는다.
  const startedRef = useRef(false);

  const run = useCallback(async () => {
    if (!sessionCode) return;
    setError(null);
    setPhase("analyzing");
    try {
      const analysis = await runAnalysis(sessionCode);
      setTopicCount(analysis.topicCount);

      setPhase("planning");
      const curriculum = await createCurriculum(sessionCode);
      setStepCount(curriculum.steps.length);

      setPhase("done");
    } catch (e) {
      setError(toMessage(e));
      setPhase("failed");
    }
  }, [sessionCode]);

  useEffect(() => {
    if (!hydrated) return;
    if (!sessionCode) {
      router.replace("/exam-info");
      return;
    }
    if (startedRef.current) return;
    startedRef.current = true;
    void run();
  }, [hydrated, sessionCode, router, run]);

  const retry = () => {
    startedRef.current = true;
    void run();
  };

  const completeCount = phase === "analyzing" ? 0 : phase === "planning" ? 1 : phase === "done" ? 2 : 0;
  const running = phase === "analyzing" || phase === "planning";

  const heading =
    phase === "done"
      ? "벼락치기 맵이 완성됐어요!"
      : phase === "failed"
        ? "분석에 실패했어요"
        : phase === "planning"
          ? "남은 시간에 맞춰 짜는 중…"
          : "시험에 나올 것만 고르는 중…";

  return (
    <div className="min-h-screen bg-white text-[#222]">
      <AppHeader />
      <main className="mx-auto max-w-[820px] px-6 py-12 md:px-10 md:pb-24 md:pt-14">
        <FlowSteps current={2} />
        <section className="rounded-[22px] border border-[#FFE0C4] bg-[linear-gradient(180deg,#FFFDFB,#FFF3E8)] px-6 py-12 text-center sm:px-12">
          <div className="relative mx-auto mb-7 w-fit">
            {running && <div className="absolute inset-[-18px] animate-pulse-ring rounded-full bg-[#FF7A00]" />}
            <div className="relative flex size-[132px] items-center justify-center rounded-full bg-white shadow-[0_12px_30px_rgba(255,122,0,.18)]">
              <Ghost
                width={76}
                mood={phase === "done" ? "excited" : phase === "failed" ? "worried" : "plain"}
                className={running ? "animate-bob" : undefined}
              />
            </div>
          </div>
          <div className="mb-2.5 text-[13px] font-bold text-[#E85D00]">AI 자료 분석</div>
          <h1 className="font-jua mb-3 text-[34px] tracking-[-1px] sm:text-[40px]">{heading}</h1>
          <p className="mb-8 text-[14.5px] leading-[1.7] text-[#888]">
            {phase === "done"
              ? `학습 주제 ${topicCount ?? 0}개 · STEP ${stepCount ?? 0}개`
              : phase === "failed"
                ? "자료를 다시 확인하거나 잠시 후 다시 시도해 주세요."
                : "자료가 많으면 1~2분 걸릴 수 있어요. 창을 닫지 말아 주세요."}
          </p>

          <div className="mx-auto grid max-w-[540px] gap-2.5 text-left">
            {TASKS.map((task, index) => {
              const done = index < completeCount;
              const active = running && index === completeCount;
              return (
                <div
                  key={task.key}
                  className={`flex items-center gap-3 rounded-xl border px-4 py-3.5 ${done || active ? "border-[#FFE0C4] bg-white" : "border-transparent bg-white/50"}`}
                >
                  <span
                    className={`flex size-6 shrink-0 items-center justify-center rounded-full text-xs ${done ? "bg-[#FF7A00] text-white" : active ? "animate-pulse bg-[#FFF3E8] text-[#E85D00]" : "bg-[#eee] text-[#aaa]"}`}
                  >
                    {done ? "✓" : index + 1}
                  </span>
                  <span className={`text-[13.5px] ${done || active ? "font-bold text-[#222]" : "text-[#aaa]"}`}>
                    {task.label}
                  </span>
                </div>
              );
            })}
          </div>

          {error && (
            <p role="alert" className="mx-auto mt-6 max-w-[540px] rounded-xl border border-[#F5C2C7] bg-[#FDECEE] px-4 py-3 text-left text-[13.5px] text-[#B02A37]">
              {error}
            </p>
          )}

          {phase === "done" && (
            <PrimaryButton className="mt-8" onClick={() => router.push("/curriculum")}>
              완성된 플랜 맵 보기
            </PrimaryButton>
          )}
          {phase === "failed" && (
            <div className="mt-8 flex flex-wrap justify-center gap-3">
              <PrimaryButton onClick={retry}>다시 시도</PrimaryButton>
              <SecondaryButton onClick={() => router.push("/upload")}>자료 다시 올리기</SecondaryButton>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
