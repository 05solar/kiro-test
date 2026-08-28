"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { AppHeader, Ghost, PrimaryButton, SecondaryButton, SpeechBubble } from "@/app/_components/ui";
import { useSessionStore } from "@/app/_components/session-store";
import { useExamStore, usePlanStore } from "@/app/_components/store";
import { getSession, toMessage } from "@/lib/api";

/** 서버가 발급하는 코드 형식. 입력값도 같은 규칙으로 먼저 걸러 낸다. */
const CODE_LENGTH = 8;

export default function HomePage() {
  const router = useRouter();
  const setSessionCode = useSessionStore((state) => state.setSessionCode);
  const clearSession = useSessionStore((state) => state.clear);
  const resetExam = useExamStore((state) => state.reset);
  const resetPlan = usePlanStore((state) => state.reset);

  /**
   * 처음부터 다시 시작한다.
   *
   * <p>이동만 하면 브라우저에 남아 있던 과목명·시험 일시·진행 상태가 그대로 되살아난다.
   * "새로 시작"이라고 눌렀는데 지난 값이 채워져 있으면, 사용자는 그것을 자기가 방금
   * 입력한 값으로 착각한 채 넘어간다. 세 곳을 모두 비우고 간다.
   *
   * <p>세션 코드도 지운다. 남겨 두면 새 시험 정보가 옛 세션에 덮어써진다.
   */
  const startFresh = () => {
    clearSession();
    resetExam();
    resetPlan();
    router.push("/exam-info");
  };

  const [resuming, setResuming] = useState(false);
  const [code, setCode] = useState("");
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const inputRef = useRef<HTMLInputElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  // 입력칸을 열면 바로 칠 수 있어야 한다. 스크롤도 함께 옮긴다.
  useEffect(() => {
    if (!resuming) return;
    inputRef.current?.focus();
    panelRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [resuming]);

  const resume = async () => {
    const trimmed = code.trim().toUpperCase();
    if (trimmed.length !== CODE_LENGTH) {
      setError(`코드는 ${CODE_LENGTH}자리입니다.`);
      inputRef.current?.focus();
      return;
    }

    setChecking(true);
    setError(null);
    try {
      // 저장하기 전에 서버에 실제로 있는 코드인지 확인한다.
      // 없는 코드를 저장하면 다음 화면마다 오류가 나 어디서 잘못됐는지 알기 어렵다.
      const session = await getSession(trimmed);
      setSessionCode(session.sessionCode);

      // 어디까지 진행했는지에 따라 이어갈 화면이 다르다.
      if (session.status === "READY" || session.status === "IN_PROGRESS" || session.status === "COMPLETED") {
        router.push("/curriculum");
      } else if (session.availableStudyMinutes === null) {
        router.push("/exam-info");
      } else {
        router.push("/upload");
      }
    } catch (e) {
      setError(toMessage(e));
    } finally {
      setChecking(false);
    }
  };

  return (
    <div className="min-h-screen bg-white text-[#222]">
      <AppHeader />
      <main className="relative overflow-clip">
        {/*
          배경 반구 — 중심을 화면 오른쪽 맨끝에 두고 절반만 안으로 들어오게 한다.
          지름을 화면 너비(105vw)로 잡아 반구의 끝이 화면 가로 중앙을 살짝 넘어온다.
          overflow-clip 이 없으면 바깥 절반이 스크롤을 만들고 위아래로 삐져나온다.
        */}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute right-0 top-1/2 size-[105vw] -translate-y-1/2 translate-x-1/2 rounded-full bg-[#FFF3E8]"
        />
        <section className="relative mx-auto grid max-w-[1240px] items-center gap-10 px-6 py-16 md:grid-cols-[1.15fr_.85fr] md:px-10 md:pb-24 md:pt-[88px]">
          <div>
            <h1 className="font-jua m-0 mb-[22px] text-[48px] leading-[1.14] tracking-[-2px] text-[#222] sm:text-[66px]">
              내일까지<br />해야 하는데<span className="text-[#FF7A00]">.</span>
            </h1>
            <p className="mb-[38px] max-w-[500px] text-[17px] leading-[1.65] text-[#666] sm:text-[19px]">
              시험 자료와 남은 시간을 알려주면<br />
              <span className="font-bold text-[#FF7A00]">벼락치기 로드맵</span>과{" "}
              <span className="font-bold text-[#FF7A00]">퀴즈</span>를 만들어줄게!
            </p>

            <div className="flex flex-wrap items-center gap-3">
              <PrimaryButton
                className="px-[30px] py-[17px] text-[16.5px]"
                onClick={startFresh}
              >
                벼락치기 새로 시작하기
              </PrimaryButton>
              <SecondaryButton
                className="px-[30px] py-[17px] text-[16.5px]"
                aria-expanded={resuming}
                onClick={() => setResuming((open) => !open)}
              >
                벼락치기 이어하기
              </SecondaryButton>
            </div>

            {resuming && (
              <div
                ref={panelRef}
                className="mt-6 max-w-[500px] rounded-[18px] border border-[#FFE0C4] bg-[#FFFDFB] p-6"
              >
                <label className="block">
                  <span className="mb-2 block text-[14px] font-bold">세션 코드</span>
                  <input
                    ref={inputRef}
                    value={code}
                    maxLength={CODE_LENGTH}
                    autoComplete="off"
                    placeholder="ex ) 7K2M9QXF"
                    // 코드는 대문자와 숫자만 쓴다. 소문자로 쳐도 알아서 맞춰 준다.
                    onChange={(event) => {
                      setCode(event.target.value.toUpperCase());
                      setError(null);
                    }}
                    onKeyDown={(event) => {
                      if (event.key === "Enter") void resume();
                    }}
                    className={`form-input font-mono tracking-[2px] ${error ? "form-input-error" : ""}`}
                  />
                </label>
                <p className="mt-2 text-[12.5px] leading-[1.6] text-[#666]">
                  벼락치기를 시작할 때 받은 8자리 코드예요. 다른 기기에서도 같은 코드로 이어집니다.
                </p>

                {error && (
                  <p role="alert" className="mt-3 text-[13.5px] font-bold text-[#E03131]">
                    {error}
                  </p>
                )}

                <PrimaryButton
                  className="mt-4 w-full py-[15px] text-[15.5px]"
                  disabled={checking}
                  onClick={() => void resume()}
                >
                  {checking ? "확인하는 중…" : "이어하기"}
                </PrimaryButton>
              </div>
            )}
          </div>

          {/* 말풍선은 항상 캐릭터 머리 위에 둔다. 배경은 화면 오른쪽의 거대한 반구가 담당한다. */}
          <div className="relative flex h-[480px] flex-col items-center justify-center gap-3 md:h-[560px]">
            <SpeechBubble tail="bottom-left" className="font-jua relative z-10">
              아직 안 늦었어!
            </SpeechBubble>
            <Ghost
              width={250}
              className="animate-bob relative drop-shadow-[0_14px_22px_rgba(255,122,0,.25)]"
            />
          </div>
        </section>
      </main>
    </div>
  );
}
