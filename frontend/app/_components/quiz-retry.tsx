"use client";

import { useState } from "react";
import { CheckMini, Ghost, PrimaryButton, SecondaryButton } from "@/app/_components/ui";
import { type QuizReviewItemResponse } from "@/lib/api";

/**
 * 이미 푼 문제를 다시 풀어 보는 연습.
 *
 * <p><b>기록되지 않는다.</b> 답안은 최초 1회만 저장하도록 설계돼 있다
 * ({@code UNIQUE(session_id, quiz_id)}). 정답을 본 뒤 답을 고쳐 점수를 올릴 수 있으면
 * 채점 기록이 성취도의 근거가 되지 못한다. 그래서 서버를 부르지 않고, 이미 받아 둔
 * {@code correctIndex} 로 화면에서만 맞춰 본다.
 *
 * <p>STEP 하나짜리(퀴즈 내역)와 여러 STEP 을 모은 것(전체 정리) 둘 다 이 컴포넌트를 쓴다.
 * 채점하고 넘기는 동작이 같은데 화면마다 따로 두면 한쪽만 고치는 일이 생긴다.
 */

/** 다시 풀 문제 한 개. 여러 STEP 을 모아 풀 때는 어느 STEP 것인지 함께 보여 준다. */
export type RetryQuizItem = {
  quiz: QuizReviewItemResponse;
  /** "STEP 3" 같은 꼬리표. STEP 하나만 풀 때는 넘기지 않는다 — 이미 아는 것이다. */
  stepLabel?: string;
};

/** 보기 번호를 사람이 읽는 기호로. 백엔드는 0부터 센다. */
const OPTION_MARKS = ["①", "②", "③", "④"];

export function RetryQuiz({
  items,
  onExit,
  exitLabel = "정리로 돌아가기",
}: {
  items: RetryQuizItem[];
  onExit: () => void;
  exitLabel?: string;
}) {
  const [index, setIndex] = useState(0);
  const [picked, setPicked] = useState<number | null>(null);
  const [hits, setHits] = useState(0);
  const [finished, setFinished] = useState(false);

  const current = items[index];

  const pick = (option: number) => {
    if (picked !== null) return;
    setPicked(option);
    if (option === current.quiz.correctIndex) setHits((n) => n + 1);
  };

  const next = () => {
    if (index + 1 >= items.length) {
      setFinished(true);
      return;
    }
    setIndex((n) => n + 1);
    setPicked(null);
  };

  const restart = () => {
    setIndex(0);
    setPicked(null);
    setHits(0);
    setFinished(false);
  };

  if (items.length === 0) {
    return null;
  }

  if (finished) {
    const all = hits === items.length;
    return (
      <section className="rounded-[18px] border border-[#FFE0C4] bg-[#FFFDFB] px-6 py-10 text-center">
        <Ghost width={130} mood={all ? "happy" : "smile"} />
        <h2 className="font-jua mb-2 mt-5 text-[26px]">
          {all ? "이번엔 전부 맞혔어요!" : `${items.length}문제 중 ${hits}문제`}
        </h2>
        <p className="mb-7 text-[14px] leading-[1.7] text-[#666]">
          연습이라 점수에는 반영되지 않아요.
          <br />
          처음 푼 기록은 그대로 남아 있어요.
        </p>
        <div className="flex flex-wrap justify-center gap-3">
          <PrimaryButton onClick={restart}>한 번 더 풀기</PrimaryButton>
          <SecondaryButton onClick={onExit}>{exitLabel}</SecondaryButton>
        </div>
      </section>
    );
  }

  const quiz = current.quiz;
  const revealed = picked !== null;

  return (
    <section className="rounded-[18px] border border-[#eee] px-5 py-6 sm:px-7">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <span className="text-[12.5px] font-bold text-[#888]">
          {index + 1} / {items.length}
        </span>
        <div className="flex flex-wrap items-center gap-2">
          <span className="rounded-full bg-[#FFF3E8] px-2.5 py-1 text-[11px] font-bold text-[#E85D00]">
            연습 · 기록되지 않아요
          </span>
          <button
            type="button"
            onClick={onExit}
            className="cursor-pointer border-0 bg-transparent p-0 text-[12.5px] font-bold text-[#666] hover:text-[#E85D00]"
          >
            그만두기
          </button>
        </div>
      </div>

      {/* 남은 양이 보여야 끝까지 간다. 문제 수가 많을수록 중요하다. */}
      <div className="mb-5 h-1.5 overflow-hidden rounded-full bg-[#FFF3E8]">
        <div
          className="h-full rounded-full bg-[#FF7A00] transition-[width] duration-300"
          style={{ width: `${Math.round((index / items.length) * 100)}%` }}
        />
      </div>

      {current.stepLabel && (
        <div className="mb-2 text-[12px] font-bold text-[#888]">{current.stepLabel}</div>
      )}
      <h2 className="mb-5 text-[16.5px] font-bold leading-[1.55]">{quiz.question}</h2>

      <div className="grid gap-2">
        {quiz.options.map((option, optionIndex) => {
          const isCorrect = optionIndex === quiz.correctIndex;
          const isPicked = optionIndex === picked;
          return (
            <button
              key={optionIndex}
              type="button"
              disabled={revealed}
              onClick={() => pick(optionIndex)}
              className={`flex items-start gap-2.5 rounded-xl border px-4 py-3.5 text-left text-[14px] leading-[1.5] transition-colors ${
                !revealed
                  ? "cursor-pointer border-[#eee] bg-white hover:border-[#FFE0C4]"
                  : isCorrect
                    ? "border-[#FF7A00] bg-[#FFF3E8] font-bold"
                    : isPicked
                      ? "border-[#F5C2C7] bg-[#FDECEE]"
                      : "border-[#eee] bg-[#fafafa] text-[#999]"
              }`}
            >
              <span className="shrink-0">{OPTION_MARKS[optionIndex]}</span>
              <span className="min-w-0 flex-1 break-keep">{option}</span>
              {revealed && isCorrect && <CheckMini size={13} />}
            </button>
          );
        })}
      </div>

      {revealed && (
        <>
          {quiz.explanation && (
            <p className="mt-4 rounded-xl bg-[#FFFDFB] px-4 py-3.5 text-[13.5px] leading-[1.7] text-[#666]">
              {quiz.explanation}
            </p>
          )}
          <PrimaryButton className="mt-5 w-full" onClick={next}>
            {index + 1 >= items.length ? "결과 보기" : "다음 문제"}
          </PrimaryButton>
        </>
      )}
    </section>
  );
}
