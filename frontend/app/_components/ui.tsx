"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type CSSProperties } from "react";
import { useExamStore } from "@/app/_components/store";
import { useHydrated } from "@/app/_components/use-hydrated";

/**
 * 캐릭터 상태.
 *
 * 새 다섯 가지가 실제 이미지이고, 과거 SVG 시절의 무드 이름은 호출부를 한꺼번에 바꾸지
 * 않기 위해 받아서 기본 이미지로 흡수한다.
 *
 * <pre>
 * default   평상시 (구 eyes/plain/neutral/worried 포함)
 * progress  AI 가 일하는 중 (분석·퀴즈 생성)
 * smile     분석 완료
 * happy     퀴즈를 잘 마쳤을 때
 * sad       오답이 많을 때 / 실패
 * </pre>
 */
type GhostMood =
  | "default"
  | "progress"
  | "smile"
  | "happy"
  | "sad"
  // 과거 무드 — 전부 default 이미지로 그린다
  | "eyes"
  | "plain"
  | "worried"
  | "neutral"
  | "excited";

const THUNDER_SRC: Record<string, string> = {
  progress: "/character/thunder-progress.png",
  smile: "/character/thunder-smile.png",
  happy: "/character/thunder-happy.png",
  sad: "/character/thunder-sad.png",
};

type GhostProps = {
  width?: number;
  mood?: GhostMood;
  /** 외곽에서 퍼져나가는 주황 그라데이션 링. 본문 캐릭터에는 켜고, 로고처럼 작은 곳은 끈다. */
  ripple?: boolean;
  className?: string;
  style?: CSSProperties;
};

/**
 * 번개 캐릭터. 배경이 투명한 PNG 하나를 그대로 그린다 — 뒤에 원판·배경을 깔지 않는다.
 *
 * <p>이름은 과거 유령 캐릭터 시절의 것이지만 호출부가 많아 유지한다.
 */
export function Ghost({
  width = 62,
  mood = "default",
  ripple = true,
  className,
  style,
}: GhostProps) {
  const src = THUNDER_SRC[mood] ?? "/character/thunder-default.png";
  return (
    <span
      className={`relative inline-block ${className ?? ""}`}
      style={{ width, height: width, ...style }}
      aria-hidden="true"
    >
      {ripple && (
        <>
          <span className="thunder-ripple-ring" />
          <span className="thunder-ripple-ring" />
        </>
      )}
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={src}
        alt=""
        width={width}
        height={width}
        draggable={false}
        className="relative z-10 size-full select-none object-contain"
      />
    </span>
  );
}

export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <div className="flex items-center gap-2.5">
      <Ghost width={26} ripple={false} />
      {!compact && <span className="font-jua text-[21px] tracking-[-0.5px] text-[#222]">내일까지</span>}
    </div>
  );
}

/** examDate/examTime으로 targetTs를 만든다. 값이 없거나 파싱 불가면 null. */
function resolveTargetTs(examDate: string, examTime: string): number | null {
  if (!examDate || !examTime) return null;
  const targetTs = new Date(`${examDate}T${examTime}:00`).getTime();
  return Number.isNaN(targetTs) ? null : targetTs;
}

/**
 * 남은 시간(ms)을 문자열로.
 * - 유효하지 않거나 0 이하: 00:00:00 (compact는 00:00)
 * - 24시간 미만: 기존 HH:MM:SS(초 단위) 유지
 * - 24시간 이상: "N일 M시간" 또는 "N시간 M분" 처럼 읽기 쉬운 형태로 축약
 */
function formatCountdown(ms: number | null, compact: boolean): string {
  if (ms === null || !Number.isFinite(ms) || ms <= 0) {
    return compact ? "00:00" : "00:00:00";
  }
  const totalSeconds = Math.floor(ms / 1000);
  const totalHours = Math.floor(totalSeconds / 3600);
  const pad = (value: number) => String(value).padStart(2, "0");

  if (totalHours >= 24) {
    const days = Math.floor(totalHours / 24);
    const restHours = totalHours % 24;
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    if (days > 0) {
      return restHours > 0 ? `${days}일 ${restHours}시간` : `${days}일`;
    }
    return minutes > 0 ? `${totalHours}시간 ${minutes}분` : `${totalHours}시간`;
  }

  const mm = Math.floor((totalSeconds % 3600) / 60);
  const ss = totalSeconds % 60;
  return compact ? `${pad(totalHours)}:${pad(mm)}:${pad(ss)}` : `${totalHours}시간 ${pad(mm)}분 ${pad(ss)}초`;
}

export function Countdown({ compact = false }: { compact?: boolean }) {
  const hydrated = useHydrated();
  const examDate = useExamStore((state) => state.examDate);
  const examTime = useExamStore((state) => state.examTime);
  const [diff, setDiff] = useState<number | null>(null);

  const targetTs = resolveTargetTs(examDate, examTime);

  useEffect(() => {
    if (targetTs === null) return;
    let id: number | undefined;
    // 매 tick마다 남은 시간을 "새로" 계산한다(감산 누적 방식 폐기).
    const tick = () => {
      const remaining = targetTs - Date.now();
      setDiff(Math.max(remaining, 0));
      if (remaining <= 0 && id !== undefined) window.clearInterval(id);
    };
    tick();
    // 아직 남은 시간이 있을 때만 interval을 등록(과거 시각이면 미등록).
    if (targetTs - Date.now() > 0) {
      id = window.setInterval(tick, 250);
    }
    return () => {
      if (id !== undefined) window.clearInterval(id);
    };
  }, [targetTs]);

  const cls = "font-jua tabular-nums tracking-[-0.2px] text-[#E85D00]";

  // 첫 렌더(하이드레이션 전)는 항상 placeholder → 서버 마크업과 일치.
  if (!hydrated) {
    return <span className={cls}>{compact ? "--:--:--" : "--:--:--"}</span>;
  }

  if (targetTs === null) {
    return <span className={cls}>{compact ? "미설정" : "시험 정보 미설정"}</span>;
  }

  if (diff === null) {
    return <span className={cls}>{compact ? "--:--:--" : "--:--:--"}</span>;
  }

  return <span className={cls}>{formatCountdown(diff, compact)}</span>;
}

export function AppHeader() {
  const router = useRouter();
  const navigate = (path: string) => router.push(path);

  return (
    <header className="sticky top-0 z-50 flex h-[68px] items-center gap-7 border-b border-[#eee] bg-white/90 px-4 backdrop-blur-lg sm:px-8">
      <button type="button" className="shrink-0 cursor-pointer border-0 bg-transparent p-0" onClick={() => navigate("/")} aria-label="처음으로">
        <Logo />
      </button>
      <nav className="hidden items-center gap-1 lg:flex" aria-label="주요 페이지">
        <NavButton onClick={() => navigate("/")}>처음으로</NavButton>
        <NavButton onClick={() => navigate("/exam-info")}>시험 정보</NavButton>
        <NavButton onClick={() => navigate("/curriculum")}>플랜 맵</NavButton>
        <NavButton onClick={() => navigate("/study/3")}>학습</NavButton>
        <NavButton onClick={() => navigate("/quiz/3")}>퀴즈</NavButton>
      </nav>
      <div className="flex-1" />
      <div className="flex items-center gap-2.5 rounded-full border border-[#FFE0C4] bg-[#FFF3E8] px-3 py-2 sm:px-4">
        <span className="relative inline-flex size-2" aria-hidden="true">
          <span className="absolute inset-0 animate-pulse-ring rounded-full bg-[#FF7A00]" />
          <span className="absolute inset-0 rounded-full bg-[#FF7A00]" />
        </span>
        <span className="hidden text-xs text-[#888] sm:inline">시험까지</span>
        <span className="text-sm font-bold sm:text-[15px]"><Countdown compact /></span>
      </div>
    </header>
  );
}

function NavButton({ children, onClick }: { children: React.ReactNode; onClick: () => void }) {
  return (
    <button
      type="button"
      className="cursor-pointer rounded-lg border-0 bg-transparent px-[13px] py-[7px] text-[13px] font-medium text-[#888] transition-colors hover:bg-[#FFF3E8] hover:text-[#E85D00]"
      onClick={onClick}
    >
      {children}
    </button>
  );
}

export function PrimaryButton({ children, onClick, className = "", type = "button", disabled = false }: {
  children: React.ReactNode;
  onClick?: () => void;
  className?: string;
  type?: "button" | "submit";
  disabled?: boolean;
}) {
  return (
    <button
      type={type}
      disabled={disabled}
      className={`cursor-pointer rounded-xl border-0 bg-[#FF7A00] px-[30px] py-4 text-base font-bold text-white shadow-[0_6px_18px_rgba(255,122,0,.26)] transition-colors hover:bg-[#E85D00] disabled:cursor-not-allowed disabled:opacity-50 ${className}`}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

export function SecondaryButton({
  children,
  onClick,
  className = "",
  "aria-expanded": ariaExpanded,
}: {
  children: React.ReactNode;
  onClick?: () => void;
  className?: string;
  /** 눌러서 무언가를 펼치는 버튼일 때. 스크린리더가 상태를 읽는다. */
  "aria-expanded"?: boolean;
}) {
  return (
    <button
      type="button"
      aria-expanded={ariaExpanded}
      className={`cursor-pointer rounded-xl border border-[#eee] bg-white px-6 py-4 text-[15px] text-[#888] transition-colors hover:border-[#FFE0C4] hover:text-[#E85D00] ${className}`}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

export function CheckIcon() {
  return (
    <svg width="26" height="26" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M5 13l5 5L19 7" stroke="#E85D00" strokeWidth="3.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/** 목록 표시용 작은 체크. 텍스트 체크 문자(✓) 대신 쓴다 — 기본 이모티콘·딩뱃 금지. */
export function CheckMini({ size = 12, color = "#E85D00" }: { size?: number; color?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" className="shrink-0">
      <path d="M4 13l5.5 5.5L20 6" stroke={color} strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/**
 * 캐릭터 말풍선.
 *
 * 기존에는 페이지마다 다른 크기와 모양으로 흩어져 있었고, 글씨가 작아 배경에 묻혔다.
 * 꼬리를 그려 누가 말하는지 분명히 하고, 본문보다 크게 잡아 먼저 읽히게 한다.
 *
 * @param tail 꼬리가 붙는 방향. 캐릭터가 있는 쪽을 가리킨다.
 */
export function SpeechBubble({
  children,
  tail = "bottom-left",
  className = "",
}: {
  children: React.ReactNode;
  tail?: "bottom-left" | "bottom-right" | "left";
  className?: string;
}) {
  const tailPosition =
    tail === "left"
      ? "left-[-9px] top-1/2 -translate-y-1/2"
      : tail === "bottom-right"
        ? "right-6 bottom-[-9px]"
        : "left-6 bottom-[-9px]";

  return (
    <div
      className={`relative inline-block rounded-[16px] border-2 border-[#FFD3A8] bg-white px-4 py-2.5 text-[14.5px] font-bold leading-[1.5] tracking-[-.2px] text-[#222] shadow-[0_5px_16px_rgba(255,122,0,.15)] sm:text-[15.5px] ${className}`}
    >
      {children}
      {/* 테두리와 배경을 각각 찍어 꼬리에도 선이 이어지게 한다. */}
      <span
        aria-hidden="true"
        className={`absolute size-[13px] rotate-45 border-b-2 border-r-2 border-[#FFD3A8] bg-white ${tailPosition}`}
        style={tail === "left" ? { transform: "rotate(135deg)" } : undefined}
      />
    </div>
  );
}

/** 필수 입력 표시. 라벨 옆에 붙인다. */
export function RequiredMark() {
  return (
    <span className="ml-1 text-[#E03131]" aria-hidden="true">
      *
    </span>
  );
}
