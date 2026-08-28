"use client";

import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { AppHeader, Ghost, PrimaryButton, SecondaryButton, SourceBadge, SourceNotice } from "@/app/_components/ui";
import { OPTION_TAGS } from "@/app/_components/data";
import { useSessionStore } from "@/app/_components/session-store";
import { useCurriculum } from "@/app/_components/use-curriculum";
import { useSession } from "@/app/_components/use-session";
import {
  ApiError,
  answerQuiz,
  generateQuizzes,
  getQuizzes,
  toMessage,
  type QuizAnswerResponse,
  type QuizListResponse,
} from "@/lib/api";
import { quizIdByNumber, toQuestions } from "@/lib/api/adapt";
import type { Question } from "@/lib/quiz";

export default function QuizPage() {
  return (
    <Suspense fallback={<QuizLoading />}>
      <QuizRoute />
    </Suspense>
  );
}

function QuizRoute() {
  const router = useRouter();
  const params = useParams<{ stepId: string }>();
  const stepId = Number(params.stepId);

  const sessionCode = useSessionStore((state) => state.sessionCode);
  const curriculum = useCurriculum();
  const topicId = curriculum.topicIds.get(stepId);

  const [list, setList] = useState<QuizListResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false);

  // 개발 모드는 effect 를 두 번 실행한다. 퀴즈 생성은 AI 호출이라 과금되므로 한 번만 부른다.
  const startedRef = useRef(false);

  const load = useCallback(async () => {
    if (!sessionCode || !topicId) return;
    setError(null);
    try {
      // 이미 만든 퀴즈가 있으면 그것을 쓴다. 새로 만들면 또 과금된다.
      setList(await getQuizzes(sessionCode, topicId));
    } catch (e) {
      if (e instanceof ApiError && e.code === "QUIZ_NOT_FOUND") {
        setGenerating(true);
        try {
          setList(await generateQuizzes(sessionCode, topicId));
        } catch (genError) {
          setError(toMessage(genError));
        } finally {
          setGenerating(false);
        }
        return;
      }
      setError(toMessage(e));
    }
  }, [sessionCode, topicId]);

  useEffect(() => {
    if (curriculum.loading) return;
    if (!sessionCode) {
      router.replace("/exam-info");
      return;
    }
    if (!topicId) {
      // 복습 단계이거나 계획에 없는 번호다. 퀴즈를 만들 대상이 없다.
      return;
    }
    if (startedRef.current) return;
    startedRef.current = true;
    void load();
  }, [curriculum.loading, sessionCode, topicId, router, load]);

  if (curriculum.loading) return <QuizLoading />;
  if (!topicId) return <EmptyQuiz stepId={stepId} />;
  if (error) return <QuizError stepId={stepId} message={error} onRetry={() => void load()} />;
  if (generating) return <QuizLoading generating />;
  if (!list) return <QuizLoading />;

  const questions = toQuestions(list);
  if (!questions.length) return <EmptyQuiz stepId={stepId} />;

  return (
    <QuizView
      key={stepId}
      stepId={stepId}
      topicTitle={list.topicTitle}
      questions={questions}
      quizIds={quizIdByNumber(list)}
    />
  );
}

/**
 * 문제를 하나씩 풀고, 고른 즉시 서버에 보낸다.
 *
 * <p><b>채점은 서버가 한다.</b> 조회 응답에는 정답이 없다. 정답을 화면에 내려보내면
 * 개발자 도구로 다 보인다. 답안을 제출해야 정오답과 해설이 온다.
 */
function QuizView({
  stepId,
  topicTitle,
  questions,
  quizIds,
}: {
  stepId: number;
  topicTitle: string;
  questions: Question[];
  quizIds: Map<number, string>;
}) {
  const router = useRouter();
  const sessionCode = useSessionStore((state) => state.sessionCode);
  // 이 문제들이 강의자료에서 나온 것인지, 일반적인 교과 지식에서 나온 것인지.
  const { source } = useSession();

  const [currentIndex, setCurrentIndex] = useState(0);
  const [picked, setPicked] = useState<number | null>(null);
  const [result, setResult] = useState<QuizAnswerResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [correctCount, setCorrectCount] = useState(0);

  const question = questions[currentIndex];
  const answered = result !== null;
  const progress = Math.round(((currentIndex + (answered ? 1 : 0)) / questions.length) * 100);

  const pick = async (index: number) => {
    if (answered || submitting) return;
    const quizId = quizIds.get(question.id);
    if (!sessionCode || !quizId) return;

    setPicked(index);
    setSubmitting(true);
    setError(null);
    try {
      const answer = await answerQuiz(sessionCode, quizId, index);
      setResult(answer);
      if (answer.correct) setCorrectCount((count) => count + 1);
    } catch (e) {
      // 실패하면 고른 표시를 되돌린다. 서버에 기록되지 않았는데 고른 것처럼 두면 안 된다.
      setPicked(null);
      setError(toMessage(e));
    } finally {
      setSubmitting(false);
    }
  };

  const next = () => {
    if (currentIndex < questions.length - 1) {
      setCurrentIndex((index) => index + 1);
      setPicked(null);
      setResult(null);
      return;
    }
    // 최종 점수는 결과 화면이 서버에서 다시 읽는다. 여기서 계산한 값을 넘기지 않는다.
    router.push(`/quiz-result/${stepId}`);
  };

  return (
    <div className="min-h-screen bg-white text-[#222]">
      <AppHeader />
      <main className="mx-auto max-w-[820px] px-6 pb-[90px] pt-12 sm:px-10">
        <div className="mb-3.5 flex items-center justify-between">
          <span className="text-[13px] font-bold text-[#FF7A00]">STEP {stepId} 확인 퀴즈</span>
          <span className="text-[13px] text-[#888]">
            {currentIndex + 1} / {questions.length}
          </span>
        </div>
        <div className="mb-2 flex flex-wrap items-center gap-2.5">
          <span className="text-[13px] text-[#888]">{topicTitle}</span>
          <SourceBadge source={source} />
        </div>
        <SourceNotice source={source} className="mb-4" />
        <div className="mb-9 h-2 overflow-hidden rounded-full bg-[#FFF3E8]">
          <div className="h-full rounded-full bg-[#FF7A00] transition-[width] duration-300" style={{ width: `${progress}%` }} />
        </div>

        <section className="rounded-[20px] border border-[#eee] p-6 sm:p-9">
          <h1 className="mb-7 text-[22px] leading-[1.5] tracking-[-.5px] sm:text-2xl">{question.question}</h1>
          <div className="grid gap-3">
            {question.options.map((option, index) => {
              const isAnswer = answered && index === result.correctIndex;
              const isPicked = index === picked;
              let stateClass = "border-[#eee] bg-white text-[#222] hover:border-[#FFE0C4]";
              if (isAnswer) stateClass = "border-[#FF7A00] bg-[#FFF3E8] font-bold text-[#E85D00]";
              else if (answered && isPicked) stateClass = "border-[#eee] bg-[#FAFAFA] text-[#888]";
              else if (answered) stateClass = "border-[#eee] bg-white text-[#888]";
              return (
                <button
                  key={option}
                  type="button"
                  disabled={answered || submitting}
                  onClick={() => void pick(index)}
                  className={`flex w-full items-center gap-3.5 rounded-[14px] border-[1.5px] px-5 py-[18px] text-left text-base leading-[1.5] transition-all ${answered || submitting ? "cursor-default" : "cursor-pointer"} ${stateClass}`}
                >
                  <span className="flex size-7 shrink-0 items-center justify-center rounded-full border border-[#eee] text-[13px] font-bold">
                    {OPTION_TAGS[index]}
                  </span>
                  <span>{option}</span>
                </button>
              );
            })}
          </div>

          {error && (
            <p role="alert" className="mt-5 rounded-xl border border-[#F5C2C7] bg-[#FDECEE] px-4 py-3 text-[13.5px] text-[#B02A37]">
              {error}
            </p>
          )}

          {answered && (
            <div className="mt-6 flex flex-col items-start gap-4 border-t border-[#eee] pt-6 sm:flex-row sm:items-center">
              <Ghost width={44} mood={result.correct ? "smile" : "worried"} className="animate-bob-small shrink-0" />
              <div className="flex-1 text-[14.5px] leading-[1.7]">
                {result.correct
                  ? `정답! ${result.explanation}`
                  : `아쉬워요. 정답은 ${OPTION_TAGS[result.correctIndex]} — ${result.explanation}`}
              </div>
              <PrimaryButton className="shrink-0 px-[26px] py-3.5 text-[15px]" onClick={next}>
                {currentIndex >= questions.length - 1 ? "결과 보기" : "다음 문제"}
              </PrimaryButton>
            </div>
          )}
        </section>

        <p className="mt-5 text-center text-[13px] text-[#888]">
          맞힌 문제 {correctCount} / {questions.length}
        </p>
      </main>
    </div>
  );
}

function QuizLoading({ generating = false }: { generating?: boolean }) {
  return (
    <div className="min-h-screen bg-white text-[#222]">
      <AppHeader />
      <main className="mx-auto max-w-[820px] animate-pulse px-6 py-12 sm:px-10">
        <div className="mb-8 h-3 w-36 rounded bg-[#FFF3E8]" />
        <div className="rounded-[20px] border border-[#eee] p-9">
          <div className="mb-8 h-7 w-3/4 rounded bg-[#eee]" />
          <div className="grid gap-3">
            {[1, 2, 3, 4].map((item) => (
              <div key={item} className="h-16 rounded-[14px] bg-[#fafafa]" />
            ))}
          </div>
          <p className="mt-6 text-center text-sm text-[#888]">
            {generating ? "자료를 보고 문제를 만드는 중… 30초쯤 걸려요." : "문제 불러오는 중…"}
          </p>
        </div>
      </main>
    </div>
  );
}

function QuizError({ stepId, message, onRetry }: { stepId: number; message: string; onRetry: () => void }) {
  const router = useRouter();
  return (
    <div className="min-h-screen bg-white text-[#222]">
      <AppHeader />
      <main className="mx-auto max-w-[720px] px-6 py-24 text-center">
        <Ghost width={70} mood="worried" className="animate-bob-small mx-auto mb-5" />
        <h1 className="font-jua mb-3 text-3xl">퀴즈를 준비하지 못했어요</h1>
        <p className="mb-8 text-[14.5px] leading-7 text-[#888]">{message}</p>
        <div className="flex flex-wrap justify-center gap-3">
          <PrimaryButton onClick={onRetry}>다시 시도</PrimaryButton>
          <SecondaryButton onClick={() => router.push(`/study/${stepId}`)}>학습 화면으로</SecondaryButton>
        </div>
      </main>
    </div>
  );
}

function EmptyQuiz({ stepId }: { stepId: number }) {
  const router = useRouter();
  return (
    <div className="min-h-screen bg-white text-[#222]">
      <AppHeader />
      <main className="mx-auto max-w-[720px] px-6 py-24 text-center">
        <Ghost width={70} mood="worried" className="animate-bob-small mx-auto mb-5" />
        <h1 className="font-jua mb-3 text-3xl">이 STEP은 퀴즈가 없어요</h1>
        <p className="mb-8 text-[14.5px] leading-7 text-[#888]">복습 단계이거나 아직 학습을 마치지 않은 단계입니다.</p>
        <PrimaryButton onClick={() => router.push(`/study/${stepId}`)}>학습 화면으로 돌아가기</PrimaryButton>
      </main>
    </div>
  );
}
