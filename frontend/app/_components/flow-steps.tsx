"use client";

import { useRouter } from "next/navigation";

const FLOW = [
  ["1", "시험 정보"],
  ["2", "자료 업로드"],
  ["3", "AI 분석"],
  ["4", "플랜 완성"],
] as const;

export function FlowSteps({ current }: { current: number }) {
  const router = useRouter();
  const paths = ["/exam-info", "/upload", "/analysis", "/curriculum"];

  return (
    <ol className="mb-9 flex w-full items-center" aria-label="플랜 생성 단계">
      {FLOW.map(([number, label], index) => (
        <li key={number} className={`flex items-center ${index < FLOW.length - 1 ? "flex-1" : ""}`}>
          <button
            type="button"
            onClick={() => index < current && router.push(paths[index])}
            disabled={index >= current}
            className="flex items-center gap-2 border-0 bg-transparent p-0 text-left disabled:cursor-default"
          >
            <span className={`flex size-7 items-center justify-center rounded-full text-xs font-bold ${index <= current ? "bg-[#FF7A00] text-white" : "bg-[#eee] text-[#666]"}`}>
              {number}
            </span>
            <span className={`hidden text-xs font-bold sm:inline ${index <= current ? "text-[#E85D00]" : "text-[#8a8a8a]"}`}>{label}</span>
          </button>
          {index < FLOW.length - 1 && <span className={`mx-3 h-px flex-1 ${index < current ? "bg-[#FF7A00]" : "bg-[#eee]"}`} />}
        </li>
      ))}
    </ol>
  );
}
