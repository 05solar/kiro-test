"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { FlowSteps } from "@/app/_components/flow-steps";
import { AppHeader, Countdown, Ghost, PrimaryButton } from "@/app/_components/ui";
import { usePlanStore, useExamStore, type PrepState } from "@/app/_components/store";
import { useSessionStore } from "@/app/_components/session-store";
import { useHydrated } from "@/app/_components/use-hydrated";
import { createSession, toMessage, updateExam } from "@/lib/api";
import { toExamAt } from "@/lib/api/adapt";

const LEVELS: { label: string; value: PrepState }[] = [
  { label: "아예 안 봤어요", value: "none" },
  { label: "한 번 훑었어요", value: "skimmed" },
  { label: "복습만 필요해요", value: "review" },
];

const DEFAULTS = {
  subject: "자료구조",
  examDate: "2026-08-28",
  examTime: "09:00",
  range: "3장 스택 ~ 7장 그래프",
  prepState: "none" as PrepState,
};

export default function ExamInfoPage() {
  const router = useRouter();
  const hydrated = useHydrated();
  const setExamInfo = useExamStore((state) => state.setExamInfo);
  // zustand v5에서 새 객체 반환은 무한 리렌더를 유발하므로 필드별로 선택한다.
  const storedSubject = useExamStore((state) => state.subject);
  const storedExamDate = useExamStore((state) => state.examDate);
  const storedExamTime = useExamStore((state) => state.examTime);
  const storedRange = useExamStore((state) => state.range);
  const storedPrepState = useExamStore((state) => state.prepState);

  const completedStepCount = usePlanStore((state) => state.completedSteps.length);

  const [subject, setSubject] = useState(DEFAULTS.subject);
  const [examDate, setExamDate] = useState(DEFAULTS.examDate);
  const [examTime, setExamTime] = useState(DEFAULTS.examTime);
  const [range, setRange] = useState(DEFAULTS.range);
  const [prepState, setPrepState] = useState<PrepState>(DEFAULTS.prepState);
  const [showResetConfirm, setShowResetConfirm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const sessionCode = useSessionStore((state) => state.sessionCode);
  const setSessionCode = useSessionStore((state) => state.setSessionCode);

  // 하이드레이션 이후에만 저장된 값으로 프리필(SSR 마크업과 first render 일치 보장).
  useEffect(() => {
    if (!hydrated) return;
    // 저장된 값 프리필. 헬퍼 함수 경유로 호출해 effect 본문의 직접 setState를 피한다.
    const prefill = () => {
      if (storedSubject) setSubject(storedSubject);
      if (storedExamDate) setExamDate(storedExamDate);
      if (storedExamTime) setExamTime(storedExamTime);
      if (storedRange) setRange(storedRange);
      if (storedPrepState) setPrepState(storedPrepState);
    };
    prefill();
  }, [hydrated, storedSubject, storedExamDate, storedExamTime, storedRange, storedPrepState]);

  /**
   * 세션을 만들고 시험 정보를 서버에 저장한 뒤 다음 화면으로 간다.
   *
   * 로컬 스토어에도 남기지만 그건 화면 프리필용이다. 남은 학습 시간의 기준은
   * 서버가 정한다 — min(입력한 시간, 지금부터 시험까지 남은 실제 시간).
   * 양쪽이 각자 계산하면 화면과 서버의 값이 어긋난다.
   */
  const commitAndContinue = async () => {
    setSubmitting(true);
    setError(null);
    try {
      // 세션이 없으면 새로 만든다. 이미 있으면 그 세션의 시험 정보를 고친다.
      const code = sessionCode ?? (await createSession()).sessionCode;
      if (code !== sessionCode) setSessionCode(code);

      setExamInfo({ subject, examDate, examTime, range, prepState });
      const available = useExamStore.getState().availableMinutes;
      await updateExam(code, {
        subject,
        examAt: toExamAt(examDate, examTime),
        availableStudyMinutes: available ?? 180,
      });
      router.push("/upload");
    } catch (e) {
      setError(toMessage(e));
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    // 기존 진행 상황이 있으면 초기화 여부를 먼저 확인시킨다.
    // (커리큘럼 페이지의 planSignature 비교로 실제 초기화가 일어나므로, 여기서는 저장 전 안내만 담당)
    if (hydrated && completedStepCount > 0) {
      setShowResetConfirm(true);
      return;
    }
    void commitAndContinue();
  };

  return (
    <div className="min-h-screen bg-white text-[#222]">
      <AppHeader />
      <main className="mx-auto max-w-[1080px] px-6 py-12 md:px-10 md:pb-24 md:pt-14">
        <FlowSteps current={0} />
        <div className="mb-2.5 text-[13px] font-bold text-[#FF7A00]">STEP 1 / 2</div>
        <h1 className="font-jua mb-2 text-4xl tracking-[-1px]">시험 정보를 알려주세요</h1>
        <p className="mb-9 text-[15px] text-[#888]">남은 시간은 자동으로 계산됩니다.</p>
        <div className="grid items-start gap-6 lg:grid-cols-[1fr_340px]">
          <form className="rounded-[18px] border border-[#eee] p-6 sm:p-[34px]" onSubmit={handleSubmit}>
            <div className="grid gap-6">
              <label className="block">
                <span className="mb-[9px] block text-[13.5px] font-bold">과목명</span>
                <input name="subject" value={subject} onChange={(event) => setSubject(event.target.value)} className="form-input" />
              </label>
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="block">
                  <span className="mb-[9px] block text-[13.5px] font-bold">시험 날짜</span>
                  <input name="examDate" type="date" value={examDate} onChange={(event) => setExamDate(event.target.value)} className="form-input" />
                </label>
                <label className="block">
                  <span className="mb-[9px] block text-[13.5px] font-bold">시험 시간</span>
                  <input name="examTime" type="time" value={examTime} onChange={(event) => setExamTime(event.target.value)} className="form-input" />
                </label>
              </div>
              <label className="block">
                <span className="mb-[9px] block text-[13.5px] font-bold">시험 범위</span>
                <input name="range" value={range} onChange={(event) => setRange(event.target.value)} className="form-input" />
                <span className="mt-2 block text-[12.5px] text-[#888]">교재 목차나 강의 슬라이드 제목을 그대로 붙여도 됩니다.</span>
              </label>
              <fieldset>
                <legend className="mb-[11px] text-[13.5px] font-bold">지금 준비 상태</legend>
                <div className="flex flex-wrap gap-2.5">
                  {LEVELS.map((item) => (
                    <button
                      key={item.value}
                      type="button"
                      onClick={() => setPrepState(item.value)}
                      className={`cursor-pointer rounded-full border px-[18px] py-[11px] text-sm transition-colors ${prepState === item.value ? "border-[#FF7A00] bg-[#FFF3E8] font-bold text-[#E85D00]" : "border-[#eee] bg-white text-[#888] hover:border-[#FFE0C4] hover:text-[#E85D00]"}`}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
              </fieldset>
              <PrimaryButton type="submit" className="mt-1.5 w-full py-[17px] text-[16.5px]">벼락치기 플랜 만들기</PrimaryButton>
            </div>
          </form>
          <aside className="grid gap-4">
            <div className="rounded-[18px] border border-[#FFE0C4] bg-[#FFF3E8] p-7 text-center">
              <div className="mb-3.5 text-[13px] font-bold text-[#E85D00]">시험까지 남은 시간</div>
              <div className="text-[40px]"><Countdown /></div>
              <div className="mt-4 border-t border-[#FFE0C4] pt-4 text-[13px] leading-[1.7] text-[#888]">
                휴식·수면 시간을 빼고 <b className="text-[#222]">실제 공부 가능 시간</b>만 플랜에 반영합니다.
              </div>
            </div>
            <div className="flex items-center gap-3.5 rounded-[18px] border border-[#eee] px-[22px] py-5">
              <Ghost width={46} mood="worried" className="animate-bob-small shrink-0" />
              <p className="text-[13.5px] leading-[1.65]">범위가 넓네… 그래도 <b className="text-[#E85D00]">중요한 것부터</b> 순서대로 짜볼게.</p>
            </div>
          </aside>
        </div>
      </main>

      {showResetConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-6" role="dialog" aria-modal="true">
          <div className="w-full max-w-[420px] rounded-2xl bg-white p-7 shadow-xl">
            <h2 className="font-jua mb-3 text-xl">플랜을 새로 만들까요?</h2>
            <p className="mb-6 text-[14.5px] leading-[1.6] text-[#555]">
              새 플랜을 만들면 기존 진행 상황(STEP {completedStepCount}개 완료)이 초기화됩니다. 계속할까요?
            </p>
            <div className="flex justify-end gap-2.5">
              <button
                type="button"
                onClick={() => setShowResetConfirm(false)}
                className="cursor-pointer rounded-xl border border-[#eee] bg-white px-5 py-2.5 text-[14px] text-[#555] transition-colors hover:border-[#ddd]"
              >
                취소
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowResetConfirm(false);
                  void commitAndContinue();
                }}
                className="cursor-pointer rounded-xl border-0 bg-[#FF7A00] px-5 py-2.5 text-[14px] font-bold text-white transition-colors hover:bg-[#E85D00]"
              >
                새로 시작
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
