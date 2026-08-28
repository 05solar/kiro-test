"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { AppHeader, Ghost, PrimaryButton, SecondaryButton } from "@/app/_components/ui";
import { useSessionStore } from "@/app/_components/session-store";
import { useCurriculum } from "@/app/_components/use-curriculum";
import {
  getQuizResults,
  getQuizzes,
  regenerateQuizzes,
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

  const [generating, setGenerating] = useState(false);
  const [generateError, setGenerateError] = useState<string | null>(null);

  /**
   * 같은 범위로 새 문제를 만든다.
   *
   * <p>실제 AI 를 부르므로 요청 중에는 버튼을 막는다. 두 번 눌리면 그만큼 과금된다.
   * 서버도 같은 Topic 의 중복 생성을 막지만, 눌린 뒤에 막는 것보다 안 눌리게 하는 편이 낫다.
   *
   * <p>화면에 들어오는 것만으로는 절대 부르지 않는다. 사용자가 누를 때만 부른다.
   */
  const startNewQuiz = async () => {
    if (!sessionCode || !topicId || generating) return;
    setGenerating(true);
    setGenerateError(null);
    try {
      await regenerateQuizzes(sessionCode, topicId);
      router.push(`/quiz/${stepId}`);
    } catch (e) {
      setGenerateError(toMessage(e));
      setGenerating(false);
    }
    // 성공하면 화면을 옮기므로 generating 을 되돌리지 않는다.
    // 되돌리면 이동 직전에 버튼이 잠깐 다시 눌리는 상태가 된다.
  };

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

        {generateError && (
          <p role="alert" className="mb-4 rounded-xl border border-[#F5C2C7] bg-[#FDECEE] px-4 py-3 text-[13.5px] text-[#B02A37]">
            {generateError}
          </p>
        )}

        <div className="flex flex-wrap gap-3">
          {/*
            같은 범위로 다른 문제를 만든다. 이미 낸 문제를 다시 보는 것이 아니다.
            실제 AI 를 부르므로 요청 중에는 막는다.
          */}
          <PrimaryButton disabled={generating} onClick={() => void startNewQuiz()}>
            {generating ? "새 문제를 만드는 중…" : "새로운 퀴즈 풀기"}
          </PrimaryButton>
          <SecondaryButton onClick={() => router.push("/curriculum")}>커리큘럼으로 돌아가기</SecondaryButton>
          <SecondaryButton onClick={() => router.push(`/study/${stepId}`)}>학습 내용 다시 보기</SecondaryButton>
        </div>
        <p className="mt-3 text-[12.5px] leading-[1.7] text-[#888]">
          같은 학습 범위에서 <b>이번과 다른 문제</b>를 새로 만듭니다. 지금까지 푼 기록은 그대로 남아요.
        </p>
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
