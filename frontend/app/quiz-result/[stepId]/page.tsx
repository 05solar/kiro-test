"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { AppHeader, Ghost, PrimaryButton, SecondaryButton } from "@/app/_components/ui";
import { useSessionStore } from "@/app/_components/session-store";
import { useCurriculum } from "@/app/_components/use-curriculum";
import {
  getQuizResults,
  getQuizzes,
  toMessage,
  type QuizListResponse,
  type QuizResultsResponse,
} from "@/lib/api";

/** 이 비율 이상 맞히면 통과로 본다. */
const PASS_RATIO = 0.6;

/**
 * 퀴즈 결과.
 *
 * <p>점수를 화면에서 계산하지 않는다. 채점 기록은 서버가 갖고 있고,
 * 이 화면은 그것을 읽어 보여주기만 한다. 예전에는 앞 화면이 쿼리스트링으로
 * 점수를 넘겼는데, 주소만 고치면 아무 점수나 만들 수 있었다.
 */
export default function QuizResultPage() {
  const router = useRouter();
  const params = useParams<{ stepId: string }>();
  const stepId = Number(params.stepId);

  const sessionCode = useSessionStore((state) => state.sessionCode);
  const curriculum = useCurriculum();
  const topicId = curriculum.topicIds.get(stepId);

  const [results, setResults] = useState<QuizResultsResponse | null>(null);
  const [quizzes, setQuizzes] = useState<QuizListResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isInteger(stepId)) router.replace("/curriculum");
  }, [router, stepId]);

  /*
   * 두 곳에서 받아 합친다.
   *
   * 채점 기록에는 문제 본문이 없다. quizId, 고른 번호, 정오답뿐이다.
   * 문제와 보기는 퀴즈 조회로 따로 받아 quizId 로 이어 붙인다.
   */
  useEffect(() => {
    if (!sessionCode || !topicId) return;
    let active = true;
    void Promise.all([getQuizResults(sessionCode, topicId), getQuizzes(sessionCode, topicId)])
      .then(([resultData, quizData]) => {
        if (!active) return;
        setResults(resultData);
        setQuizzes(quizData);
      })
      .catch((e) => {
        if (active) setError(toMessage(e));
      });
    return () => {
      active = false;
    };
  }, [sessionCode, topicId]);

  if (error) {
    return (
      <div className="min-h-screen bg-white text-[#222]">
        <AppHeader />
        <main className="mx-auto max-w-[720px] px-6 py-24 text-center">
          <Ghost width={70} mood="worried" className="animate-bob-small mx-auto mb-5" />
          <h1 className="font-jua mb-3 text-3xl">결과를 불러오지 못했어요</h1>
          <p className="mb-8 text-[14.5px] leading-7 text-[#888]">{error}</p>
          <PrimaryButton onClick={() => router.push("/curriculum")}>커리큘럼으로 돌아가기</PrimaryButton>
        </main>
      </div>
    );
  }

  if (curriculum.loading || !results || !quizzes) return <ResultLoading />;

  const { correctAnswers: score, totalQuestions: total, scorePercentage: percentage } = results;
  const passed = total > 0 && score / total >= PASS_RATIO;

  // quizId 로 문제 본문을 붙인다. 채점 기록만으로는 무엇을 틀렸는지 보여줄 수 없다.
  const quizById = new Map(quizzes.quizzes.map((quiz) => [quiz.id, quiz]));
  const answered = results.results
    .map((item) => ({ ...item, quiz: quizById.get(item.quizId) }))
    .filter((item) => item.quiz !== undefined)
    .sort((a, b) => (a.quiz?.order ?? 0) - (b.quiz?.order ?? 0));

  const weakQuestions = answered.filter((item) => !item.correct);
  const strongQuestions = answered.filter((item) => item.correct);

  return (
    <div className="min-h-screen bg-white text-[#222]">
      <AppHeader />
      <main className="mx-auto max-w-[900px] px-6 pb-24 pt-[52px] sm:px-10">
        <section className="mb-6 flex flex-col items-center gap-6 rounded-[22px] border border-[#FFE0C4] bg-[#FFF3E8] p-7 text-center sm:flex-row sm:gap-8 sm:p-9 sm:text-left">
          <Ghost width={104} mood={passed ? "excited" : "worried"} className="animate-bob shrink-0 drop-shadow-[0_12px_18px_rgba(255,122,0,.22)]" />
          <div>
            {passed && <div className="mb-2 inline-block rounded-full bg-[#FF7A00] px-3 py-1 text-xs font-bold text-white">STEP {stepId} 완료!</div>}
            <div className="mb-[9px] text-[13px] font-bold text-[#E85D00]">STEP {stepId} 퀴즈 결과</div>
            <div className="font-jua mb-2 text-[38px] tracking-[-1.2px]">
              {score} / {total} 정답 · {percentage}% ·{" "}
              <span className={passed ? "text-[#E85D00]" : "text-[#888]"}>{passed ? "통과" : "재도전"}</span>
            </div>
            <p className="text-[15px] leading-[1.6]">
              {passed
                ? "학습 기록이 저장됐어요. 다음 STEP으로 이동해도 됩니다."
                : `${Math.round(PASS_RATIO * 100)}% 이상 맞히면 통과예요. 틀린 문제를 다시 확인해 보세요.`}
            </p>
          </div>
        </section>

        <div className="mb-7 grid gap-5 md:grid-cols-2">
          <section className="rounded-[18px] border border-[#eee] p-7">
            <h2 className="mb-4 text-[13px] font-bold text-[#FF7A00]">틀린 문제 — 시험 직전에 다시 보기</h2>
            <div className="grid gap-2.5">
              {weakQuestions.length ? (
                weakQuestions.map((item) => (
                  <div key={item.quizId} className="rounded-xl border border-[#FFE0C4] bg-[#FFF3E8] px-4 py-3.5">
                    <div className="mb-1.5 text-[14.5px] font-bold">
                      {item.quiz!.order}. {item.quiz!.question}
                    </div>
                    <div className="text-[13px] leading-[1.7] text-[#888]">
                      내가 고른 답 — {item.quiz!.options[item.selectedIndex] ?? "-"}
                    </div>
                  </div>
                ))
              ) : (
                <div className="rounded-xl border border-[#FFE0C4] bg-[#FFF3E8] px-4 py-3.5 text-[14.5px] font-bold">전부 맞혔어요</div>
              )}
            </div>
          </section>
          <section className="rounded-[18px] border border-[#eee] p-7">
            <h2 className="mb-4 text-[13px] font-bold text-[#888]">맞힌 문제</h2>
            <div className="grid gap-2.5">
              {strongQuestions.map((item) => (
                <div key={item.quizId} className="flex items-start gap-[11px] rounded-xl border border-[#eee] px-4 py-3.5">
                  <span className="mt-0.5 text-[13px] text-[#E85D00]">✓</span>
                  <span className="text-[14.5px] text-[#888]">{item.quiz!.question}</span>
                </div>
              ))}
            </div>
          </section>
        </div>

        <div className="flex flex-wrap gap-3">
          <PrimaryButton onClick={() => router.push("/curriculum")}>커리큘럼으로 돌아가기</PrimaryButton>
          <SecondaryButton onClick={() => router.push(`/study/${stepId}`)}>학습 내용 다시 보기</SecondaryButton>
        </div>
      </main>
    </div>
  );
}

function ResultLoading() {
  return (
    <div className="min-h-screen bg-white text-[#222]">
      <AppHeader />
      <main className="mx-auto flex max-w-[900px] items-center justify-center px-10 py-24">
        <Ghost width={80} mood="plain" className="animate-bob" />
        <span className="ml-4 text-sm text-[#888]">결과 불러오는 중…</span>
      </main>
    </div>
  );
}
