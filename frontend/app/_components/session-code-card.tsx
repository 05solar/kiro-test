"use client";

import { useEffect, useState } from "react";
import { CheckMini } from "@/app/_components/ui";
import { useSessionStore } from "@/app/_components/session-store";
import { useHydrated } from "@/app/_components/use-hydrated";

/**
 * 발급받은 8자리 세션 코드를 보여주는 칸.
 *
 * <p><b>이 코드가 이 서비스의 유일한 접근 키다.</b> 회원가입이 없으므로 코드를 잃으면
 * 올린 자료도 만든 계획도 되찾을 수 없다. 그런데 화면 어디에도 코드를 보여주는 곳이
 * 없었다 — 사용자가 자기 코드를 알 방법이 없었다.
 *
 * <p>브라우저에 저장해 두긴 하지만 그것만으로는 부족하다. 다른 기기에서 이어하려면
 * 사람이 그 값을 알아야 하고, 시크릿 창이나 저장소 정리로 언제든 사라진다.
 */
export function SessionCodeCard({ className = "" }: { className?: string }) {
  const hydrated = useHydrated();
  const sessionCode = useSessionStore((state) => state.sessionCode);
  const [copied, setCopied] = useState(false);

  // 복사 표시는 잠깐만 띄운다. 계속 남아 있으면 다음에 눌렀는지 알 수 없다.
  useEffect(() => {
    if (!copied) return;
    const id = window.setTimeout(() => setCopied(false), 2000);
    return () => window.clearTimeout(id);
  }, [copied]);

  // 서버 렌더와 첫 그림을 맞춘다. 코드는 브라우저에만 있다.
  if (!hydrated || !sessionCode) return null;

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(sessionCode);
      setCopied(true);
    } catch {
      // 클립보드를 막아 둔 브라우저가 있다. 코드는 눈에 보이므로 받아 적으면 된다.
      setCopied(false);
    }
  };

  return (
    <section
      aria-label="세션 코드"
      className={`shrink-0 rounded-[16px] border border-[#FFE0C4] bg-[#FFF3E8] px-4 py-3 ${className}`}
    >
      <div className="flex items-center gap-3">
        <div className="min-w-0">
          <div className="mb-0.5 text-[11.5px] font-bold text-[#E85D00]">내 세션 코드</div>
          <div className="font-mono text-[19px] font-bold leading-tight tracking-[2px] text-[#222] sm:text-[21px]">
            {sessionCode}
          </div>
        </div>
        <button
          type="button"
          onClick={() => void copy()}
          className="flex shrink-0 cursor-pointer items-center gap-1.5 rounded-lg border border-[#FFE0C4] bg-white px-3 py-2 text-[12.5px] font-bold text-[#E85D00] transition-colors hover:border-[#FF7A00]"
        >
          {copied ? (
            <>
              <CheckMini size={12} />
              복사됨
            </>
          ) : (
            "복사"
          )}
        </button>
      </div>
      <p className="mt-2 text-[11.5px] leading-[1.6] text-[#7A4A16]">
        코드를 통해서 학습을 계속 이어갈 수 있어요
      </p>
    </section>
  );
}
