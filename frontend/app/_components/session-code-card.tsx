"use client";

import { usePathname } from "next/navigation";
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
/**
 * 세션 코드를 읽고 복사하는 상태.
 *
 * <p>카드와 막대 두 모양이 같은 동작을 하므로 로직을 여기 한 번만 둔다.
 */
function useSessionCode() {
  const hydrated = useHydrated();
  const sessionCode = useSessionStore((state) => state.sessionCode);
  const [copied, setCopied] = useState(false);

  // 복사 표시는 잠깐만 띄운다. 계속 남아 있으면 다음에 눌렀는지 알 수 없다.
  useEffect(() => {
    if (!copied) return;
    const id = window.setTimeout(() => setCopied(false), 2000);
    return () => window.clearTimeout(id);
  }, [copied]);

  const copy = async () => {
    if (!sessionCode) return;
    try {
      await navigator.clipboard.writeText(sessionCode);
      setCopied(true);
    } catch {
      // 클립보드를 막아 둔 브라우저가 있다. 코드는 눈에 보이므로 받아 적으면 된다.
      setCopied(false);
    }
  };

  // 서버 렌더와 첫 그림을 맞춘다. 코드는 브라우저에만 있다.
  return { code: hydrated ? sessionCode : null, copied, copy };
}

/**
 * 화면 위쪽 막대에 놓는 한 줄짜리 세션 코드.
 *
 * <p>넓은 화면에서만 쓴다. 좁은 화면에서는 위 막대에 로고와 남은 시간만으로도 이미 빠듯해서,
 * 그쪽은 본문의 {@link SessionCodeCard} 가 맡는다.
 *
 * <p>코드 자체가 버튼이다. 옆에 복사 버튼을 따로 두면 그만큼 자리를 더 먹는다.
 */
export function SessionCodePill() {
  const { code, copied, copy } = useSessionCode();
  const pathname = usePathname();

  /*
   * 랜딩에서는 그리지 않는다.
   *
   * 그 화면은 "새로 시작"과 "이어하기"를 고르는 자리다. 위 막대에 코드가 떠 있으면
   * 이미 어떤 세션에 들어와 있는 것처럼 보여, 새로 시작하려는 사람을 헷갈리게 한다.
   */
  if (!code || pathname === "/") return null;

  return (
    <button
      type="button"
      onClick={() => void copy()}
      aria-label={`세션 코드 ${code}. 눌러서 복사`}
      title="눌러서 복사"
      className="hidden items-center gap-2 rounded-full border border-[#eee] bg-white px-3 py-2 transition-colors hover:border-[#FFE0C4] lg:flex"
    >
      <span className="text-[11.5px] font-bold text-[#888]">코드</span>
      <span className="font-mono text-[13.5px] font-bold tracking-[1.5px] text-[#222]">{code}</span>
      <span className="flex w-[42px] justify-end text-[11.5px] font-bold text-[#E85D00]">
        {copied ? "복사됨" : "복사"}
      </span>
    </button>
  );
}

/**
 * 본문에 놓는 세션 코드 칸.
 *
 * @param wide 넓게 그린다. 분석 화면처럼 기다리는 동안 눈에 들어와야 하는 자리에 쓴다.
 */
export function SessionCodeCard({
  className = "",
  wide = false,
}: {
  className?: string;
  wide?: boolean;
}) {
  const { code, copied, copy } = useSessionCode();
  if (!code) return null;

  return (
    <section
      aria-label="세션 코드"
      className={`shrink-0 rounded-[16px] border border-[#FFE0C4] bg-[#FFF3E8] ${
        wide ? "px-6 py-5" : "px-4 py-3"
      } ${className}`}
    >
      <div className={`flex items-center gap-3 ${wide ? "justify-center" : ""}`}>
        <div className="min-w-0">
          <div className={`mb-0.5 font-bold text-[#E85D00] ${wide ? "text-[12.5px]" : "text-[11.5px]"}`}>
            내 세션 코드
          </div>
          <div
            className={`font-mono font-bold leading-tight tracking-[2px] text-[#222] ${
              wide ? "text-[26px] sm:text-[30px]" : "text-[19px] sm:text-[21px]"
            }`}
          >
            {code}
          </div>
        </div>
        <button
          type="button"
          onClick={() => void copy()}
          className={`flex shrink-0 cursor-pointer items-center gap-1.5 rounded-lg border border-[#FFE0C4] bg-white font-bold text-[#E85D00] transition-colors hover:border-[#FF7A00] ${
            wide ? "px-4 py-2.5 text-[13.5px]" : "px-3 py-2 text-[12.5px]"
          }`}
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
      <p
        className={`mt-2 leading-[1.6] text-[#7A4A16] ${
          wide ? "text-center text-[13px]" : "text-[11.5px]"
        }`}
      >
        코드를 통해서 학습을 계속 이어갈 수 있어요
      </p>
    </section>
  );
}
