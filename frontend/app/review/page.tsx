"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { AppHeader, Ghost, PrimaryButton, SecondaryButton } from "@/app/_components/ui";
import { RetryQuiz } from "@/app/_components/quiz-retry";
import { useSessionStore } from "@/app/_components/session-store";
import { useHydrated } from "@/app/_components/use-hydrated";
import {
  getSessionReview,
  toMessage,
  type QuizReviewItemResponse,
  type SessionReviewResponse,
  type StepReviewResponse,
} from "@/lib/api";

/**
 * 세션 전체 정리.
 *
 * <p>공부를 마친 뒤 한 번에 훑어보는 화면이다. 세 갈래로 나뉜다 —
 * STEP 별 요약, 푼 문제 전체, 틀린 문제만.
 *
 * <p>서버를 한 번만 부른다. 세 갈래가 같은 자료를 보므로, 탭을 옮길 때마다 다시 묻지
 * 않는다. AI 도 부르지 않는다 — 요약은 분석 때 만들어 둔 것을 그대로 쓴다.
 *
 * <p>파일로 내보내는 대신 인쇄 화면을 쓴다. 브라우저의 "PDF 로 저장"이 그대로 파일이
 * 되고, 표와 색이 화면에 보이는 대로 남는다. 인쇄용 규칙은 {@code globals.css} 의
 * {@code @media print} 에 있다.
 */

type Tab = "summary" | "all" | "wrong";

const OPTION_MARKS = ["①", "②", "③", "④"];

const TABS: { key: Tab; label: string }[] = [
  { key: "summary", label: "STEP 별 요약" },
  { key: "all", label: "푼 문제 전체" },
  { key: "wrong", label: "틀린 문제만" },
];

/** 이 STEP 에서 틀린 문제. 안 푼 문제는 틀린 것으로 세지 않는다. */
function wrongOf(step: StepReviewResponse): QuizReviewItemResponse[] {
  return step.quizzes.filter((quiz) => quiz.answered && !quiz.correct);
}

/** 이 STEP 에서 푼 문제. */
function answeredOf(step: StepReviewResponse): QuizReviewItemResponse[] {
  return step.quizzes.filter((quiz) => quiz.answered);
}

export default function ReviewPage() {
  const router = useRouter();
  const hydrated = useHydrated();
  const sessionCode = useSessionStore((state) => state.sessionCode);

  const [review, setReview] = useState<SessionReviewResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>("summary");

  useEffect(() => {
    if (!hydrated) return;
    if (!sessionCode) {
      router.replace("/");
      return;
    }
    let active = true;
    void getSessionReview(sessionCode)
      .then((data) => {
        if (active) setReview(data);
      })
      .catch((e) => {
        if (active) setError(toMessage(e));
      });
    return () => {
      active = false;
    };
  }, [hydrated, sessionCode, router]);

  // 요약은 Topic 이 있는 STEP 에만 있다. 휴식·복습 STEP 은 정리할 내용이 없다.
  const studySteps = useMemo(
    () => review?.steps.filter((step) => step.topicId !== null) ?? [],
    [review],
  );
  const stepsWithAnswers = useMemo(
    () => studySteps.filter((step) => answeredOf(step).length > 0),
    [studySteps],
  );
  const stepsWithWrong = useMemo(
    () => studySteps.filter((step) => wrongOf(step).length > 0),
    [studySteps],
  );

  /*
   * 다시 풀 문제를 STEP 을 가로질러 모은다.
   *
   * STEP 순서, 그 안에서는 문제 순서다. 자료를 읽은 차례대로 나와야 앞 STEP 에서
   * 나온 개념이 뒤 STEP 문제의 전제가 되는 흐름이 유지된다.
   *
   * 어느 STEP 의 문제인지 꼬리표를 붙인다. 여러 STEP 을 섞어 풀면 문제만 보고는
   * 무엇에 대한 질문인지 잡기 어렵다.
   */
  const toRetryItems = (steps: StepReviewResponse[], pick: (step: StepReviewResponse) => QuizReviewItemResponse[]) =>
    steps.flatMap((step) =>
      pick(step).map((quiz) => ({ quiz, stepLabel: `STEP ${step.stepOrder} · ${step.topicTitle ?? step.title}` })),
    );

  const allRetryItems = useMemo(() => toRetryItems(stepsWithAnswers, answeredOf), [stepsWithAnswers]);
  const wrongRetryItems = useMemo(() => toRetryItems(stepsWithWrong, wrongOf), [stepsWithWrong]);

  /* 어느 갈래를 다시 풀고 있는지. null 이면 목록을 본다. */
  const [retrying, setRetrying] = useState<"all" | "wrong" | null>(null);
  const retryItems = retrying === "all" ? allRetryItems : retrying === "wrong" ? wrongRetryItems : [];

  // 탭을 옮기면 풀던 것을 접는다. 다른 갈래를 보러 간 사람에게 앞 갈래의 문제를 계속 물을 수 없다.
  const selectTab = (next: Tab) => {
    setTab(next);
    setRetrying(null);
  };

  /*
   * 이 갈래를 화면에 보일지.
   *
   * 종이에는 언제나 세 갈래를 모두 찍는다 — 정리를 저장하는 사람은 셋 다 한 파일에
   * 있기를 바란다. 그래서 화면에서 감출 때도 print:block 을 남긴다.
   *
   * 다시 풀고 있는 동안에는 목록을 화면에서 걷어 낸다. 풀고 있는 문제 바로 아래에
   * 그 문제의 정답이 적힌 목록이 있으면 연습이 되지 않는다.
   */
  const visible = (key: Tab) => (tab === key && !retrying ? "" : "hidden print:block");

  if (error) {
    return (
      <div className="min-h-screen bg-white text-[#222]">
        <AppHeader />
        <main className="mx-auto max-w-[760px] px-6 py-16 text-center">
          <Ghost width={140} mood="sad" />
          <h1 className="font-jua mb-3 mt-6 text-[28px]">정리를 불러오지 못했어요</h1>
          <p className="mb-7 text-[14.5px] text-[#666]">{error}</p>
          <SecondaryButton onClick={() => router.push("/curriculum")}>플랜 맵으로</SecondaryButton>
        </main>
      </div>
    );
  }

  if (!review) {
    return (
      <div className="min-h-screen bg-white text-[#222]">
        <AppHeader />
        <main className="mx-auto max-w-[760px] px-6 py-16 text-center text-[14.5px] text-[#666]">
          정리를 모으는 중…
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-white text-[#222]">
      {/* 위 막대와 아래 이동 막대는 종이에 남을 이유가 없다. print:hidden 은 globals.css 가 맡는다. */}
      <div className="print:hidden">
        <AppHeader />
      </div>

      <main className="mx-auto max-w-[860px] px-6 py-10 md:pb-24 md:pt-12 print:max-w-none print:px-0 print:py-0">
        <header className="mb-7">
          <div className="mb-2.5 text-[13px] font-bold text-[#E85D00]">시험 정리</div>
          <h1 className="font-jua mb-2 text-[32px] leading-[1.2] tracking-[-1px] sm:text-[40px]">
            {review.subject ?? "내 시험"} 총정리
          </h1>
          <p className="text-[14px] text-[#666]">
            STEP {review.completedSteps} / {review.totalSteps} 완료 · 문제 {review.answeredQuestions}개 풀이 ·
            정답률 {review.scorePercentage}%
          </p>
        </header>

        <section className="mb-7 grid grid-cols-2 gap-2.5 sm:grid-cols-4">
          <Stat label="STEP" value={`${review.completedSteps} / ${review.totalSteps}`} />
          <Stat label="푼 문제" value={`${review.answeredQuestions} / ${review.totalQuestions}`} />
          <Stat label="맞힌 문제" value={`${review.correctAnswers}`} />
          <Stat label="틀린 문제" value={`${review.wrongAnswers}`} accent={review.wrongAnswers > 0} />
        </section>

        {/* 탭과 인쇄 버튼은 화면에서만 쓴다. 종이에는 세 갈래를 모두 이어서 찍는다. */}
        <div className="mb-6 flex flex-wrap items-center justify-between gap-3 print:hidden">
          <div className="flex flex-wrap gap-2">
            {TABS.map((item) => (
              <button
                key={item.key}
                type="button"
                onClick={() => selectTab(item.key)}
                className={`cursor-pointer rounded-full border px-4 py-2 text-[13px] font-bold transition-colors ${
                  tab === item.key
                    ? "border-[#FF7A00] bg-[#FFF3E8] text-[#E85D00]"
                    : "border-[#eee] bg-white text-[#666] hover:border-[#FFE0C4]"
                }`}
              >
                {item.label}
              </button>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {/*
              문제를 보는 갈래에서만 다시 풀 수 있다. 요약 갈래에는 풀 문제가 없다.
              STEP 을 가로질러 모아 풀기 때문에, 마지막 STEP 을 끝낸 뒤 전체를 훑는
              용도가 된다 — STEP 마다 들어가 따로 풀 필요가 없다.
            */}
            {(tab === "all" || tab === "wrong") && !retrying && (
              <RetryButton
                count={(tab === "all" ? allRetryItems : wrongRetryItems).length}
                onClick={() => setRetrying(tab)}
                label={tab === "all" ? "전체 다시 풀기" : "틀린 문제 다시 풀기"}
              />
            )}
            <button
              type="button"
              onClick={() => window.print()}
              className="cursor-pointer rounded-xl border border-[#FFE0C4] bg-white px-4 py-2.5 text-[13px] font-bold text-[#E85D00] transition-colors hover:border-[#FF7A00]"
            >
              인쇄 · PDF 로 저장
            </button>
          </div>
        </div>

        {/*
          다시 풀기는 목록 위에 얹는다. 목록을 지우지 않는 이유는 인쇄 때문이다 —
          종이에 찍히는 내용은 아래 목록들이라, 풀이 중이라고 그것을 걷어 내면
          인쇄 결과가 비어 버린다.
        */}
        {retrying && (
          <div className="mb-8 print:hidden">
            <RetryQuiz items={retryItems} onExit={() => setRetrying(null)} exitLabel="정리로 돌아가기" />
          </div>
        )}

        {/*
          화면에서는 고른 탭 하나만, 종이에는 세 갈래를 모두 찍는다.
          정리를 저장하는 사람은 셋 다 한 파일에 있기를 바란다 — 탭마다 세 번 인쇄하게 할 수는 없다.
        */}
        <div className={visible("summary")}>
          <PrintHeading>STEP 별 요약</PrintHeading>
          {studySteps.length === 0 ? (
            <Empty>정리할 STEP 이 없어요.</Empty>
          ) : (
            <div className="grid gap-4">
              {studySteps.map((step) => (
                <SummaryCard key={step.stepId} step={step} />
              ))}
            </div>
          )}
        </div>

        <div className={visible("all")}>
          <PrintHeading>푼 문제 전체</PrintHeading>
          {stepsWithAnswers.length === 0 ? (
            <Empty>아직 푼 문제가 없어요.</Empty>
          ) : (
            <div className="grid gap-7">
              {stepsWithAnswers.map((step) => (
                <StepQuizGroup key={step.stepId} step={step} items={answeredOf(step)} />
              ))}
            </div>
          )}
        </div>

        <div className={visible("wrong")}>
          <PrintHeading>틀린 문제만</PrintHeading>
          {stepsWithWrong.length === 0 ? (
            <Empty>틀린 문제가 없어요. 전부 맞혔습니다!</Empty>
          ) : (
            <div className="grid gap-7">
              {stepsWithWrong.map((step) => (
                <StepQuizGroup key={step.stepId} step={step} items={wrongOf(step)} />
              ))}
            </div>
          )}
        </div>

        <div className="mt-10 flex flex-wrap gap-3 print:hidden">
          <PrimaryButton onClick={() => window.print()}>인쇄 · PDF 로 저장</PrimaryButton>
          <SecondaryButton onClick={() => router.push("/curriculum")}>플랜 맵으로</SecondaryButton>
        </div>
      </main>
    </div>
  );
}

/**
 * 다시 풀기 버튼.
 *
 * <p>몇 문제를 풀게 되는지 버튼에 적는다. 40문제짜리인지 3문제짜리인지 모르고
 * 누르면 중간에 그만두게 된다.
 */
function RetryButton({
  count,
  label,
  onClick,
}: {
  count: number;
  label: string;
  onClick: () => void;
}) {
  // 풀 문제가 없으면 누를 것이 없다. 눌리는데 아무 일도 없으면 고장으로 보인다.
  if (count === 0) return null;

  return (
    <button
      type="button"
      onClick={onClick}
      className="cursor-pointer rounded-xl border border-[#FF7A00] bg-[#FFF3E8] px-4 py-2.5 text-[13px] font-bold text-[#E85D00] transition-colors hover:bg-[#FFE9D6]"
    >
      {label} {count}문제
    </button>
  );
}

/** 종이에서만 보이는 갈래 제목. 화면에서는 탭이 그 일을 한다. */
function PrintHeading({ children }: { children: React.ReactNode }) {
  return (
    <h2 className="font-jua mb-4 hidden text-[24px] print:block print:break-before-page">
      {children}
    </h2>
  );
}

function Empty({ children }: { children: React.ReactNode }) {
  return (
    <p className="rounded-[16px] border border-[#eee] bg-[#fafafa] px-6 py-8 text-center text-[14.5px] text-[#666]">
      {children}
    </p>
  );
}

function Stat({ label, value, accent = false }: { label: string; value: string; accent?: boolean }) {
  return (
    <div
      className={`rounded-[14px] border px-4 py-3.5 text-center ${
        accent ? "border-[#F5C2C7] bg-[#FDECEE]" : "border-[#eee] bg-white"
      }`}
    >
      <div className="mb-1 text-[11.5px] font-bold text-[#888]">{label}</div>
      <div className={`font-jua text-[21px] ${accent ? "text-[#E03131]" : "text-[#222]"}`}>{value}</div>
    </div>
  );
}

/** STEP 하나의 요약. 분석 때 만들어 둔 요약과 핵심 포인트를 그대로 옮긴다. */
function SummaryCard({ step }: { step: StepReviewResponse }) {
  const wrong = wrongOf(step).length;
  const answered = answeredOf(step).length;

  return (
    <article className="break-inside-avoid rounded-[16px] border border-[#eee] px-5 py-5 sm:px-6">
      <div className="mb-2.5 flex flex-wrap items-center gap-2">
        <span className="rounded-full bg-[#FFF3E8] px-2.5 py-1 text-[11.5px] font-bold text-[#E85D00]">
          STEP {step.stepOrder}
        </span>
        {step.status === "SKIPPED" && (
          <span className="rounded-full bg-amber-50 px-2.5 py-1 text-[11px] font-bold text-amber-700">
            시간 부족으로 제외
          </span>
        )}
        {answered > 0 && (
          <span className="text-[12px] text-[#666]">
            문제 {answered}개 중 {answered - wrong}개 정답
          </span>
        )}
      </div>

      <h3 className="mb-3 text-[18px] font-bold leading-[1.4]">{step.topicTitle ?? step.title}</h3>

      {step.summary && (
        <p className="mb-4 whitespace-pre-line text-[14.5px] leading-[1.8] text-[#444]">{step.summary}</p>
      )}

      {step.keyPoints.length > 0 && (
        <div className="rounded-[12px] bg-[#FFFDFB] px-4 py-3.5">
          <div className="mb-2 text-[12px] font-bold text-[#E85D00]">핵심 포인트</div>
          <ul className="grid gap-1.5">
            {step.keyPoints.map((point) => (
              <li key={point} className="flex gap-2 text-[13.5px] leading-[1.6] text-[#555]">
                <span className="text-[#FF7A00]">·</span>
                <span className="min-w-0 flex-1 break-keep">{point}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </article>
  );
}

/** STEP 하나에 딸린 문제 묶음. */
function StepQuizGroup({ step, items }: { step: StepReviewResponse; items: QuizReviewItemResponse[] }) {
  return (
    <section>
      <div className="mb-3 flex flex-wrap items-baseline gap-2">
        <span className="rounded-full bg-[#FFF3E8] px-2.5 py-1 text-[11.5px] font-bold text-[#E85D00]">
          STEP {step.stepOrder}
        </span>
        <h3 className="text-[16px] font-bold">{step.topicTitle ?? step.title}</h3>
        <span className="text-[12.5px] text-[#888]">{items.length}문제</span>
      </div>
      <div className="grid gap-3.5">
        {items.map((item) => (
          <QuizCard key={item.quizId} item={item} />
        ))}
      </div>
    </section>
  );
}

/** 문제 하나. 내가 고른 것과 정답을 나란히 둔다. */
function QuizCard({ item }: { item: QuizReviewItemResponse }) {
  return (
    <article
      className={`break-inside-avoid rounded-[14px] border px-4 py-4 sm:px-5 ${
        item.correct ? "border-[#eee] bg-white" : "border-[#F5C2C7] bg-[#FFFBFB]"
      }`}
    >
      <div className="mb-2 flex items-center gap-2">
        <span className="text-[11.5px] font-bold text-[#888]">문제 {item.quizOrder}</span>
        <span
          className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${
            item.correct ? "bg-[#FFF3E8] text-[#E85D00]" : "bg-[#FDECEE] text-[#E03131]"
          }`}
        >
          {item.correct ? "정답" : "오답"}
        </span>
      </div>
      <h4 className="mb-3 text-[15px] font-bold leading-[1.55]">{item.question}</h4>

      <div className="grid gap-1.5">
        {item.options.map((option, index) => {
          const isCorrect = index === item.correctIndex;
          const isMine = index === item.selectedIndex;
          return (
            <div
              key={index}
              className={`flex items-start gap-2 rounded-[9px] border px-3 py-2 text-[13px] leading-[1.5] ${
                isCorrect
                  ? "border-[#FF7A00] bg-[#FFF3E8] font-bold"
                  : isMine
                    ? "border-[#F5C2C7] bg-[#FDECEE]"
                    : "border-transparent bg-[#fafafa] text-[#666]"
              }`}
            >
              <span className="shrink-0">{OPTION_MARKS[index]}</span>
              <span className="min-w-0 flex-1 break-keep">{option}</span>
              {isCorrect && <span className="shrink-0 text-[11px] font-bold text-[#E85D00]">정답</span>}
              {isMine && !isCorrect && <span className="shrink-0 text-[11px] font-bold text-[#E03131]">내 답</span>}
            </div>
          );
        })}
      </div>

      {item.explanation && (
        <p className="mt-3 rounded-[9px] bg-[#FFFDFB] px-3.5 py-2.5 text-[13px] leading-[1.7] text-[#666]">
          {item.explanation}
        </p>
      )}
    </article>
  );
}
