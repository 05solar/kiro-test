"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { AppHeader, CheckMini, Countdown, Ghost, PrimaryButton } from "@/app/_components/ui";
import { StudyChat } from "@/app/_components/study-chat";
import { type StudyContent } from "@/app/_components/data";
import { useSessionStore } from "@/app/_components/session-store";
import { usePlanStore } from "@/app/_components/store";
import { useCurriculum } from "@/app/_components/use-curriculum";
import { useHydrated } from "@/app/_components/use-hydrated";
import { completeStep, listTopics, startStep, toMessage, type TopicResponse } from "@/lib/api";
import { toStudyContent } from "@/lib/api/adapt";

export default function StudyPage() {
  const router = useRouter();
  const params = useParams<{ stepId: string }>();
  const hydrated = useHydrated();
  const stepId = Number(params.stepId);

  const sessionCode = useSessionStore((state) => state.sessionCode);
  const rawWeakSteps = usePlanStore((state) => state.weakSteps);
  const toggleWeakStep = usePlanStore((state) => state.toggleWeakStep);
  const weakSteps = hydrated ? rawWeakSteps : [];

  // 계획과 진행 상태는 서버가 갖는다. 화면이 다시 계산하지 않는다.
  const curriculum = useCurriculum();
  const [topics, setTopics] = useState<TopicResponse[] | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);

  const completedSteps = curriculum.completedStepIds;
  const planStep = curriculum.plan?.steps.find((step) => step.id === stepId);
  const isCut = curriculum.plan?.cutStepIds.includes(stepId) ?? false;

  // 학습 내용(요약·핵심 개념)은 Topic 에 있다. 계획에는 제목과 시간만 있다.
  useEffect(() => {
    if (!sessionCode) return;
    let active = true;
    void listTopics(sessionCode)
      .then((list) => {
        if (active) setTopics(list);
      })
      .catch(() => {
        if (active) setTopics([]);
      });
    return () => {
      active = false;
    };
  }, [sessionCode]);

  const topic = topics?.find((item) => item.topicOrder === stepId);
  const content: StudyContent | undefined = topic ? toStudyContent(topic) : undefined;

  /*
   * 화면을 열면 그 단계를 시작한다.
   *
   * 실제 학습시간은 서버가 startedAt 과 완료 요청 시각으로 잰다. 이 호출이 없으면
   * 완료할 때 "시작하지 않은 단계"로 거절당한다.
   *
   * 이미 진행 중이면 서버가 시작 시각을 덮지 않고 그대로 돌려주므로 다시 눌러도 안전하다.
   * 순서를 건너뛰었거나 다른 단계가 진행 중이면 409 가 오는데, 그건 화면에 알린다.
   */
  const stepUuid = curriculum.stepIds.get(stepId);
  useEffect(() => {
    if (!sessionCode || !stepUuid || !planStep) return;
    if (completedSteps.includes(stepId)) return;
    void startStep(sessionCode, stepUuid).catch((e) => setActionError(toMessage(e)));
    // 단계가 바뀔 때만 시작한다. 다른 값 변화로 다시 부르지 않는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionCode, stepUuid]);

  /** 학습을 마치고 퀴즈로 간다. 퀴즈는 학습을 완료해야 만들 수 있다. */
  const finishAndGoToQuiz = async () => {
    if (!sessionCode || !stepUuid) return;
    setWorking(true);
    setActionError(null);
    try {
      if (!completedSteps.includes(stepId)) {
        await completeStep(sessionCode, stepUuid);
      }
      router.push(`/quiz/${stepId}`);
    } catch (e) {
      setActionError(toMessage(e));
    } finally {
      setWorking(false);
    }
  };

  useEffect(() => {
    if (!Number.isInteger(stepId)) router.replace("/curriculum");
  }, [router, stepId]);

  if (!Number.isInteger(stepId) || curriculum.loading || topics === null) {
    return <StudyLoading />;
  }

  if (!content) {
    return <StudyLoading />;
  }

  if (isCut) {
    return (
      <div className="min-h-screen bg-white text-[#222]">
        <AppHeader />
        <main className="mx-auto flex max-w-[720px] flex-col items-center px-6 py-24 text-center">
          <Ghost width={96} mood="sad" className="animate-bob-small mb-6" />
          <div className="mb-3 rounded-full bg-amber-50 px-3 py-1.5 text-xs font-bold text-amber-700">STEP {stepId} 제외</div>
          <h1 className="font-jua mb-3 text-3xl">이 STEP은 플랜에서 제외됐어요</h1>
          <p className="mb-8 text-[14.5px] leading-7 text-[#666]">남은 시간에 맞춰 우선순위가 높은 개념부터 학습하도록 조정했어요.</p>
          <PrimaryButton onClick={() => router.push("/curriculum")}>커리큘럼으로 돌아가기</PrimaryButton>
        </main>
      </div>
    );
  }

  return (
    <StudyView
      key={stepId}
      stepId={stepId}
      content={content}
      quizFirst={planStep?.quizFirst ?? false}
      estimatedMinutes={planStep?.minutes ?? 0}
      planStepIds={curriculum.plan?.steps.map((step) => step.id) ?? []}
      cutStepIds={curriculum.plan?.cutStepIds ?? []}
      stepTitles={new Map((curriculum.raw?.steps ?? []).map((step) => [step.order, step.title]))}
      completedSteps={completedSteps}
      weakSteps={weakSteps}
      working={working}
      actionError={actionError}
      onFinish={() => void finishAndGoToQuiz()}
      onToggleWeak={() => toggleWeakStep(stepId)}
    />
  );
}

type StudyViewProps = {
  stepId: number;
  content: StudyContent;
  quizFirst: boolean;
  estimatedMinutes: number;
  planStepIds: number[];
  cutStepIds: number[];
  /** 서버가 준 단계 제목. 자료마다 달라 하드코딩할 수 없다. */
  stepTitles: Map<number, string>;
  completedSteps: number[];
  weakSteps: number[];
  working: boolean;
  actionError: string | null;
  onFinish: () => void;
  onToggleWeak: () => void;
};

function StudyView({
  stepId,
  content,
  quizFirst,
  estimatedMinutes,
  planStepIds,
  cutStepIds,
  stepTitles,
  completedSteps,
  weakSteps,
  working,
  actionError,
  onFinish,
  onToggleWeak,
}: StudyViewProps) {
  const router = useRouter();
  const [conceptsOpen, setConceptsOpen] = useState(!quizFirst);
  const isWeak = weakSteps.includes(stepId);
  const completedInPlan = planStepIds.filter((id) => completedSteps.includes(id)).length;
  const progress = planStepIds.length ? Math.round((completedInPlan / planStepIds.length) * 100) : 0;

  return (
    <div className="min-h-screen bg-white text-[#222]">
      <AppHeader />
      {/* 학습 화면은 좌우 칸을 이미 쓰고 있다. 도우미는 떠 있는 버튼으로 둔다. */}
      <StudyChat variant="floating" />
      {/*
        칸 너비를 minmax(0, …) 로 둔다. 고정 폭(272px)만 주면 트랙 크기는 그대로인데
        내용이 그보다 넓어질 때 칸 밖으로 삐져나와, 구분선을 넘어 옆 칸을 침범한다.
        min 을 0 으로 열어 줘야 안쪽의 truncate 가 실제로 걸린다.
      */}
      <div className="grid min-h-[calc(100vh-68px)] xl:grid-cols-[minmax(0,272px)_minmax(0,1fr)_minmax(0,300px)]">
        <aside className="order-2 min-w-0 border-t border-[#eee] p-6 xl:order-1 xl:border-r xl:border-t-0 xl:px-6 xl:py-8">
          <div className="mb-[26px]">
            <div className="mb-[11px] flex items-baseline justify-between"><span className="text-[12.5px] font-bold text-[#666]">전체 진행률</span><span className="font-jua text-[19px] text-[#FF7A00]">{progress}%</span></div>
            <div className="h-2 overflow-hidden rounded-full bg-[#FFF3E8]"><div className="h-full rounded-full bg-[#FF7A00] transition-[width]" style={{ width: `${progress}%` }} /></div>
          </div>
          <div className="mb-3.5 text-[12.5px] font-bold text-[#666]">STEP</div>
          <div className="grid gap-2">
            {[...stepTitles.entries()]
              .sort(([a], [b]) => a - b)
              .map(([id, title]) => ({ id, title }))
              .map((step) => {
              const active = step.id === stepId;
              const done = completedSteps.includes(step.id);
              const cut = cutStepIds.includes(step.id);
              const bookmarked = weakSteps.includes(step.id);
              return (
                <button
                  key={step.id}
                  type="button"
                  disabled={cut}
                  onClick={() => !cut && router.push(`/study/${step.id}`)}
                  className={`flex items-center gap-[11px] rounded-xl border p-3 text-left transition-colors ${
                    active
                      ? "border-[#FF7A00] bg-[#FFF3E8]"
                      : cut
                        ? "cursor-not-allowed border-[#eee] bg-[#fafafa] opacity-40"
                        : "cursor-pointer border-[#eee] bg-white hover:border-[#FFE0C4]"
                  }`}
                >
                  {done ? (
                    <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-[#FFE0C4]"><CheckMini size={11} /></span>
                  ) : active ? (
                    <span className="font-jua flex size-6 shrink-0 items-center justify-center rounded-full bg-[#FF7A00] text-[13px] text-white">{step.id}</span>
                  ) : (
                    <span className="size-6 shrink-0 rounded-full border-2 border-[#eee]" />
                  )}
                  <span className={`min-w-0 flex-1 truncate text-[13.5px] ${active ? "font-bold text-[#E85D00]" : "text-[#666]"}`}>{step.id}. {step.title}</span>
                  {bookmarked && (
                    <svg width="12" height="14" viewBox="0 0 24 28" fill="none" aria-hidden="true" className="shrink-0">
                      <title>다시 볼 개념</title>
                      <path d="M5 2h14a1 1 0 0 1 1 1v23l-8-5.5L4 26V3a1 1 0 0 1 1-1Z" fill="#F59F00" stroke="#E8590C" strokeWidth="1.6" strokeLinejoin="round" />
                    </svg>
                  )}
                </button>
              );
            })}
          </div>
          <button type="button" className="mt-[22px] w-full cursor-pointer rounded-[10px] border border-[#eee] bg-white p-3 text-[13px] text-[#666] transition-colors hover:border-[#FFE0C4] hover:text-[#E85D00]" onClick={() => router.push("/curriculum")}>맵으로 돌아가기</button>
        </aside>

        <main className="order-1 min-w-0 max-w-[900px] px-6 py-10 sm:px-14 sm:pb-20 sm:pt-11 xl:order-2">
          <div className="mb-3.5 flex items-center gap-2.5">
            <span className="rounded-full bg-[#FFF3E8] px-[11px] py-[5px] text-xs font-bold text-[#E85D00]">STEP {stepId}</span>
            <span className="text-[13px] text-[#666]">{content.chapter} · 예상 {estimatedMinutes}분</span>
            {quizFirst && <span className="rounded-full bg-amber-50 px-2 py-1 text-[11px] font-bold text-amber-700">복습 모드</span>}
          </div>
          <h1 className="font-jua mb-6 text-[27px] leading-[1.3] tracking-[-1.2px] sm:mb-[34px] sm:text-[40px]">{content.title}</h1>

          {quizFirst && !conceptsOpen && (
            <section className="mb-5 rounded-[18px] border border-[#FFE0C4] bg-[#FFFDFB] px-6 py-5 sm:px-8">
              <div className="mb-2 text-[13px] font-bold text-[#E85D00]">3줄 핵심 요약</div>
              <p className="line-clamp-3 text-[14.5px] leading-[1.75] text-[#666]">{content.summary}</p>
              <button type="button" onClick={() => setConceptsOpen(true)} className="mt-3 cursor-pointer border-0 bg-transparent p-0 text-[13px] font-bold text-[#E85D00]">개념 카드 펼치기</button>
            </section>
          )}

          {conceptsOpen && (
            <section className="mb-5 rounded-[18px] border border-[#eee] px-6 py-7 sm:px-8 sm:py-[30px]">
              <div className="mb-5 flex items-center justify-between">
                <span className="text-[13px] font-bold text-[#FF7A00]">핵심 개념 {content.concepts.length}가지</span>
                {quizFirst && <button type="button" onClick={() => setConceptsOpen(false)} className="text-xs text-[#666]">접기</button>}
              </div>
              <div className="grid gap-[22px]">
                {content.concepts.map((concept, index) => (
                  <article key={concept.title} className="flex gap-4">
                    <div className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-[#FFF3E8] text-[13px] font-bold text-[#E85D00]">{index + 1}</div>
                    <div><h2 className="mb-[7px] text-[17px] font-bold">{concept.title}</h2><p className="text-[14.5px] leading-[1.75] text-[#666]">{concept.description}</p></div>
                  </article>
                ))}
              </div>
            </section>
          )}

          {/*
            개념 아래에 이 STEP 의 요약을 둔다.
            분석 단계에서 이미 만들어 둔 값(topic.summary)인데, 지금까지는 복습 모드로
            접혀 있을 때만 보여 일반 모드에서는 화면에 나오지 않았다.

            접힌 상태에서는 위에 이미 같은 요약이 있으므로 여기서는 그리지 않는다.
          */}
          {conceptsOpen && content.summary && (
            <section className="mb-5 rounded-[18px] border border-[#FFE0C4] bg-[#FFFDFB] px-6 py-6 sm:px-8">
              <div className="mb-2.5 text-[13px] font-bold text-[#E85D00]">이 STEP 요약</div>
              <p className="whitespace-pre-line text-[14.5px] leading-[1.8]">{content.summary}</p>
            </section>
          )}

          {actionError && (
            <p role="alert" className="mb-4 rounded-xl border border-[#F5C2C7] bg-[#FDECEE] px-4 py-3 text-[13.5px] text-[#B02A37]">
              {actionError}
            </p>
          )}
          <div className="flex flex-wrap gap-3">
            {/* 퀴즈는 이 단계를 완료해야 만들 수 있다. 버튼 하나가 완료와 이동을 함께 한다. */}
            <PrimaryButton disabled={working} onClick={onFinish}>
              {working ? "저장하는 중…" : quizFirst ? "퀴즈부터 풀기" : "이해했어요 · 퀴즈 풀기"}
            </PrimaryButton>
            <button
              type="button"
              aria-pressed={isWeak}
              onClick={onToggleWeak}
              className={`cursor-pointer rounded-xl border px-6 py-4 text-[15px] transition-colors ${
                isWeak
                  ? "border-amber-400 bg-amber-100 font-bold text-amber-800"
                  : "border-[#eee] bg-white text-[#666] hover:border-amber-300 hover:text-amber-700"
              }`}
            >
              {isWeak ? "다시 볼 개념으로 표시됨" : "나중에 다시 볼 개념으로 표시"}
            </button>
          </div>
        </main>

        <aside className="order-3 min-w-0 border-t border-[#eee] p-6 xl:border-l xl:border-t-0 xl:px-6 xl:py-8">
          <div className="mb-4 rounded-2xl border border-[#FFE0C4] bg-[#FFF3E8] p-[22px] text-center">
            <div className="mb-[9px] text-xs font-bold text-[#E85D00]">시험까지</div>
            <div className="text-[28px]"><Countdown /></div>
          </div>
          <div className="mb-4 rounded-2xl border border-[#eee] p-5">
            <div className="mb-3 text-[12.5px] font-bold text-[#666]">STEP {stepId} 중요도</div>
            <div className="mb-2.5 flex items-center gap-[9px]"><span className="font-jua text-[22px] text-[#FF7A00]">{content.importanceLabel}</span><span className="text-xs text-[#666]">{content.importanceNote}</span></div>
            <div className="flex gap-[5px]"><div className="h-[7px] flex-1 rounded-full bg-[#FF7A00]" /><div className="h-[7px] flex-1 rounded-full bg-[#FF7A00]" /><div className="h-[7px] flex-1 rounded-full bg-[#FFE0C4]" /><div className="h-[7px] flex-1 rounded-full bg-[#FFE0C4]" /></div>
            {/* 요약은 본문 쪽으로 옮겼다. 같은 문장이 한 화면에 두 번 보이지 않게 한다. */}
          </div>
          <div className="rounded-2xl border border-[#eee] p-5 text-center">
            <Ghost width={68} mood={completedSteps.includes(stepId) ? "happy" : "default"} className="animate-bob-small mx-auto opacity-90" />
            <p className="mt-2.5 text-[13px] leading-[1.65]">{content.characterComment}</p>
          </div>
        </aside>
      </div>
    </div>
  );
}

function StudyLoading() {
  return (
    <div className="min-h-screen bg-white text-[#222]">
      <AppHeader />
      <main className="mx-auto max-w-[760px] px-6 py-24 text-center text-[#666]">커리큘럼으로 이동하는 중…</main>
    </div>
  );
}
