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

/** 복사가 어떻게 끝났는지. 실패를 조용히 넘기지 않으려고 성공과 나눠 둔다. */
type CopyState = "idle" | "copied" | "failed";

/**
 * 클립보드에 글자를 넣는다.
 *
 * <p>{@code navigator.clipboard} 는 https 이거나 localhost 일 때만 존재한다. EC2 에
 * IP 나 http 주소로 붙으면 객체 자체가 없어서, 그냥 부르면 예외가 난다. 그래서
 * 있는지부터 보고, 없거나 막혔으면 오래된 방법으로 한 번 더 시도한다.
 *
 * <p>구식 방법은 화면 밖에 잠깐 만든 입력칸에 값을 넣고 그것을 골라 복사하는 것이다.
 * {@code document.execCommand} 는 폐기 예정이지만 http 주소에서 동작하는 유일한 수단이라
 * 아직 대체할 것이 없다.
 *
 * @return 실제로 복사됐으면 참
 */
async function writeToClipboard(text: string): Promise<boolean> {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      // 권한이 막혔거나 창이 포커스를 잃은 경우다. 아래에서 다시 시도한다.
    }
  }

  const area = document.createElement("textarea");
  area.value = text;
  // 읽기 전용이라야 모바일 사파리에서 자판이 올라오지 않는다.
  area.setAttribute("readonly", "");
  area.style.position = "fixed";
  area.style.top = "-1000px";
  area.style.opacity = "0";
  document.body.appendChild(area);
  try {
    area.select();
    area.setSelectionRange(0, text.length);
    return document.execCommand("copy");
  } catch {
    return false;
  } finally {
    document.body.removeChild(area);
  }
}

/**
 * 세션 코드를 읽고 복사하는 상태.
 *
 * <p>카드와 막대 두 모양이 같은 동작을 하므로 로직을 여기 한 번만 둔다.
 */
function useSessionCode() {
  const hydrated = useHydrated();
  const sessionCode = useSessionStore((state) => state.sessionCode);
  const [state, setState] = useState<CopyState>("idle");

  // 결과 표시는 잠깐만 띄운다. 계속 남아 있으면 다음에 눌렀는지 알 수 없다.
  useEffect(() => {
    if (state === "idle") return;
    const id = window.setTimeout(() => setState("idle"), 2000);
    return () => window.clearTimeout(id);
  }, [state]);

  const copy = async () => {
    if (!sessionCode) return;
    // 실패도 화면에 알린다. 눌렀는데 아무 일도 없으면 고장 난 것과 구별되지 않는다.
    setState((await writeToClipboard(sessionCode)) ? "copied" : "failed");
  };

  return { code: hydrated ? sessionCode : null, state, copy };
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
  const { code, state, copy } = useSessionCode();
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
      title={state === "failed" ? "복사할 수 없어요. 코드를 직접 적어 주세요" : "눌러서 복사"}
      /*
       * 눌렸다는 것이 확실히 보여야 한다. 앞서는 42px 짜리 칸의 글씨만 "복사"에서
       * "복사됨" 으로 바뀌어서, 복사가 됐는지 버튼이 죽었는지 구별되지 않았다.
       * 칸 전체의 색을 바꿔 멀리서도 알아보게 한다.
       */
      className={`hidden cursor-pointer items-center gap-2 rounded-full border px-3 py-2 transition-colors lg:flex ${
        state === "copied"
          ? "border-[#FF7A00] bg-[#FFF3E8]"
          : state === "failed"
            ? "border-[#F5C2C7] bg-[#FDECEE]"
            : "border-[#eee] bg-white hover:border-[#FFE0C4]"
      }`}
    >
      <span className="text-[11.5px] font-bold text-[#888]">코드</span>
      {/* 실패했을 때도 코드는 그대로 보여 둔다 — 눈으로 보고 받아 적을 수 있어야 한다. */}
      <span className="select-all font-mono text-[13.5px] font-bold tracking-[1.5px] text-[#222]">{code}</span>
      <span
        className={`flex w-[52px] items-center justify-end gap-1 text-[11.5px] font-bold ${
          state === "failed" ? "text-[#E03131]" : "text-[#E85D00]"
        }`}
      >
        {state === "copied" ? (
          <>
            <CheckMini size={11} />
            복사됨
          </>
        ) : state === "failed" ? (
          "복사 실패"
        ) : (
          "복사"
        )}
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
  const { code, state, copy } = useSessionCode();
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
          {/* 한 번 누르면 통째로 선택된다. 복사가 막힌 환경에서 직접 복사할 길을 남긴다. */}
          <div
            className={`select-all font-mono font-bold leading-tight tracking-[2px] text-[#222] ${
              wide ? "text-[26px] sm:text-[30px]" : "text-[19px] sm:text-[21px]"
            }`}
          >
            {code}
          </div>
        </div>
        <button
          type="button"
          onClick={() => void copy()}
          className={`flex shrink-0 cursor-pointer items-center gap-1.5 rounded-lg border bg-white font-bold transition-colors ${
            state === "failed"
              ? "border-[#F5C2C7] text-[#E03131]"
              : "border-[#FFE0C4] text-[#E85D00] hover:border-[#FF7A00]"
          } ${wide ? "px-4 py-2.5 text-[13.5px]" : "px-3 py-2 text-[12.5px]"}`}
        >
          {state === "copied" ? (
            <>
              <CheckMini size={12} />
              복사됨
            </>
          ) : state === "failed" ? (
            "복사 실패"
          ) : (
            "복사"
          )}
        </button>
      </div>
      <p
        className={`mt-2 leading-[1.6] ${state === "failed" ? "text-[#B02A37]" : "text-[#7A4A16]"} ${
          wide ? "text-center text-[13px]" : "text-[11.5px]"
        }`}
      >
        {state === "failed"
          ? "브라우저가 복사를 막았어요. 코드를 직접 적어 두세요"
          : "코드를 통해서 학습을 계속 이어갈 수 있어요"}
      </p>
    </section>
  );
}
