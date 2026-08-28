"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { AppHeader, Ghost, SecondaryButton } from "@/app/_components/ui";
import { RetryQuiz } from "@/app/_components/quiz-retry";
import { useSessionStore } from "@/app/_components/session-store";
import { useCurriculum } from "@/app/_components/use-curriculum";
import { useHydrated } from "@/app/_components/use-hydrated";
import { getQuizReview, toMessage, type QuizReviewItemResponse, type QuizReviewResponse } from "@/lib/api";

/**
 * 한 STEP 의 퀴즈 내역.
 *
 * <p>두 가지를 한 화면에서 한다 — 지난 풀이를 되돌아보는 것과, 틀린 것만 다시 풀어
 * 보는 것. 화면을 나누면 "틀린 문제"를 두 번 찾아야 한다.
 *
 * <p><b>다시 풀기는 기록되지 않는다.</b> 답안은 최초 1회만 저장하도록 설계돼 있다
 * (UNIQUE(session, quiz)). 정답을 본 뒤 답을 고쳐 점수를 올릴 수 있으면 채점 기록이
 * 성취도의 근거가 되지 못한다. 그래서 여기서는 서버를 부르지 않고 화면에서만 채점한다.
 */

type Mode = "all" | "wrong" | "retry";

/** 보기 번호를 사람이 읽는 기호로. 백엔드는 0부터 센다. */
const OPTION_MARKS = ["①", "②", "③", "④"];

export default function QuizReviewPage() {
  const router = useRouter();
  const params = useParams<{ stepId: string }>();
  const hydrated = useHydrated();
  const stepId = Number(params.stepId);

  const sessionCode = useSessionStore((state) => state.sessionCode);
  const curriculum = useCurriculum();

  const [review, setReview] = useState<QuizReviewResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [mode, setMode] = useState<Mode>("all");

  // 내역은 Topic 기준이다. 화면의 STEP 번호를 Topic 으로 바꿔야 부를 수 있다.
  const topicId = curriculum.topicIds.get(stepId) ?? null;

  useEffect(() => {
    if (!hydrated) return;
    if (!sessionCode) {
      router.replace("/");
      return;
    }
    if (!topicId) return;

    let active = true;
    void getQuizReview(sessionCode, topicId)
      .then((data) => {
        if (active) setReview(data);
      })
      .catch((e) => {
        if (active) setError(toMessage(e));
      });
    return () => {
      active = false;
    };
  }, [hydrated, sessionCode, topicId, router]);

  const answered = useMemo(
    () => review?.items.filter((item) => item.answered) ?? [],
    [review],
  );
  const wrong = useMemo(() => answered.filter((item) => !item.correct), [answered]);

  const title = review?.topicTitle ?? `STEP ${stepId}`;

  if (error) {
    return (
      <div className="min-h-screen bg-white text-[#222]">
        <AppHeader />
        <main className="mx-auto max-w-[760px] px-6 py-16 text-center">
          <Ghost width={140} mood="sad" />
          <h1 className="font-jua mb-3 mt-6 text-[28px]">내역을 불러오지 못했어요</h1>
          <p className="mb-7 text-[14.5px] text-[#666]">{error}</p>
          <SecondaryButton onClick={() => router.push(`/study/${stepId}`)}>학습으로 돌아가기</SecondaryButton>
        </main>
      </div>
    );
  }

  if (!review) {
    return (
      <div className="min-h-screen bg-white text-[#222]">
        <AppHeader />
        <main className="mx-auto max-w-[760px] px-6 py-16 text-center text-[14.5px] text-[#666]">
          내역을 불러오는 중…
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-white text-[#222]">
      <AppHeader />
      <main className="mx-auto max-w-[760px] px-6 py-10 md:pb-24 md:pt-12">
        <button
          type="button"
          onClick={() => router.push(`/study/${stepId}`)}
          className="mb-5 cursor-pointer border-0 bg-transparent p-0 text-[13px] font-bold text-[#666] hover:text-[#E85D00]"
        >
          ← STEP {stepId} 학습으로
        </button>

        <div className="mb-2.5 text-[13px] font-bold text-[#E85D00]">
          STEP {stepId} 퀴즈 내역{review.round > 1 ? ` · ${review.round}회차` : ""}
        </div>
        <h1 className="font-jua mb-6 text-[30px] leading-[1.25] tracking-[-1px] sm:text-[36px]">{title}</h1>

        <section className="mb-7 grid grid-cols-3 gap-2.5">
          <Stat label="푼 문제" value={`${review.answeredQuestions} / ${review.totalQuestions}`} />
          <Stat label="맞힌 문제" value={`${review.answeredQuestions - review.wrongQuestions}`} />
          <Stat label="틀린 문제" value={`${review.wrongQuestions}`} accent={review.wrongQuestions > 0} />
        </section>

        {answered.length === 0 ? (
          <p className="rounded-[16px] border border-[#eee] bg-[#fafafa] px-6 py-8 text-center text-[14.5px] text-[#666]">
            아직 푼 문제가 없어요. 퀴즈를 먼저 풀어 주세요.
          </p>
        ) : (
          <>
            <div className="mb-5 flex flex-wrap gap-2">
              <Tab active={mode === "all"} onClick={() => setMode("all")}>
                전체 {answered.length}
              </Tab>
              <Tab active={mode === "wrong"} onClick={() => setMode("wrong")} disabled={wrong.length === 0}>
                틀린 문제 {wrong.length}
              </Tab>
              <Tab active={mode === "retry"} onClick={() => setMode("retry")} disabled={wrong.length === 0}>
                틀린 문제만 다시 풀기
              </Tab>
            </div>

            {mode === "retry" ? (
              <RetryQuiz
                items={wrong.map((quiz) => ({ quiz }))}
                onExit={() => setMode("wrong")}
                exitLabel="틀린 문제 다시 보기"
              />
            ) : (
              <div className="grid gap-4">
                {(mode === "wrong" ? wrong : answered).map((item) => (
                  <ReviewCard key={item.quizId} item={item} />
                ))}
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
}

function Stat({ label, value, accent = false }: { label: string; value: string; accent?: boolean }) {
  return (
    <div className={`rounded-[14px] border px-4 py-3.5 text-center ${accent ? "border-[#F5C2C7] bg-[#FDECEE]" : "border-[#eee] bg-white"}`}>
      <div className="mb-1 text-[11.5px] font-bold text-[#888]">{label}</div>
      <div className={`font-jua text-[22px] ${accent ? "text-[#E03131]" : "text-[#222]"}`}>{value}</div>
    </div>
  );
}

function Tab({
  active,
  disabled = false,
  onClick,
  children,
}: {
  active: boolean;
  disabled?: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className={`rounded-full border px-4 py-2 text-[13px] font-bold transition-colors ${
        disabled
          ? "cursor-not-allowed border-[#eee] bg-[#fafafa] text-[#bbb]"
          : active
            ? "cursor-pointer border-[#FF7A00] bg-[#FFF3E8] text-[#E85D00]"
            : "cursor-pointer border-[#eee] bg-white text-[#666] hover:border-[#FFE0C4]"
      }`}
    >
      {children}
    </button>
  );
}

/** 지난 풀이 한 문제. 내가 고른 것과 정답을 나란히 보여 준다. */
function ReviewCard({ item }: { item: QuizReviewItemResponse }) {
  return (
    <article
      className={`rounded-[16px] border px-5 py-5 sm:px-6 ${
        item.correct ? "border-[#eee] bg-white" : "border-[#F5C2C7] bg-[#FFFBFB]"
      }`}
    >
      <div className="mb-2.5 flex items-center gap-2">
        <span className="text-[11.5px] font-bold text-[#888]">문제 {item.quizOrder}</span>
        <span
          className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${
            item.correct ? "bg-[#FFF3E8] text-[#E85D00]" : "bg-[#FDECEE] text-[#E03131]"
          }`}
        >
          {item.correct ? "정답" : "오답"}
        </span>
      </div>
      <h2 className="mb-3.5 text-[15.5px] font-bold leading-[1.55]">{item.question}</h2>

      <div className="grid gap-1.5">
        {item.options.map((option, index) => {
          const isCorrect = index === item.correctIndex;
          const isMine = index === item.selectedIndex;
          return (
            <div
              key={index}
              className={`flex items-start gap-2 rounded-[10px] border px-3 py-2.5 text-[13.5px] leading-[1.5] ${
                isCorrect
                  ? "border-[#FF7A00] bg-[#FFF3E8] font-bold"
                  : isMine
                    ? "border-[#F5C2C7] bg-[#FDECEE]"
                    : "border-transparent bg-[#fafafa] text-[#666]"
              }`}
            >
              <span className="shrink-0">{OPTION_MARKS[index]}</span>
              <span className="min-w-0 flex-1 break-keep">{option}</span>
              {isCorrect && <span className="shrink-0 text-[11.5px] font-bold text-[#E85D00]">정답</span>}
              {isMine && !isCorrect && <span className="shrink-0 text-[11.5px] font-bold text-[#E03131]">내 답</span>}
            </div>
          );
        })}
      </div>

      {item.explanation && (
        <p className="mt-3.5 rounded-[10px] bg-[#FFFDFB] px-3.5 py-3 text-[13.5px] leading-[1.7] text-[#666]">
          {item.explanation}
        </p>
      )}
    </article>
  );
}

