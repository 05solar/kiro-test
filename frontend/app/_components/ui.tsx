"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type CSSProperties } from "react";
import { useExamStore } from "@/app/_components/store";
import { useHydrated } from "@/app/_components/use-hydrated";
import type { SourceLabel } from "@/lib/api/adapt";
import { SessionCodePill } from "@/app/_components/session-code-card";

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

/**
 * 화면 위쪽 막대와, 좁은 화면의 아래쪽 이동 막대.
 *
 * <p>좁은 화면에서는 이동 버튼을 <b>아래</b>에 둔다. 한 손으로 들고 쓰는 화면에서
 * 위쪽 끝은 엄지가 닿지 않는다. 위쪽에는 로고와 남은 시간만 남긴다.
 *
 * <p>아래 막대는 화면에 고정되므로 본문 마지막 줄을 가린다. 그만큼의 여백은
 * {@code globals.css} 의 body 규칙이 만든다 — 페이지마다 따로 넣으면 빠뜨리는 곳이 생긴다.
 */
export function AppHeader() {
  const router = useRouter();
  const pathname = usePathname();
  const navigate = (path: string) => router.push(path);

  /*
   * 아래 막대는 아이콘만 쓴다. 다섯 칸에 "시험 정보" 같은 말을 넣으면 줄바꿈되거나 잘린다.
   * 무엇인지는 aria-label 이 말한다.
   *
   * match 는 "지금 이 화면이 그 항목인가"다. 학습·퀴즈는 주소에 STEP 번호가 붙으므로
   * 앞부분으로 본다. 아이콘만 있는 막대에서는 현재 위치 표시가 글씨가 있을 때보다 더 중요하다.
   */
  const links: {
    label: string;
    path: string;
    icon: (props: { size?: number; color?: string }) => React.ReactElement;
    match: (pathname: string) => boolean;
  }[] = [
    { label: "처음으로", path: "/", icon: HomeIcon, match: (p) => p === "/" },
    { label: "시험 정보", path: "/exam-info", icon: CalendarIcon, match: (p) => p.startsWith("/exam-info") },
    { label: "플랜 맵", path: "/curriculum", icon: MapIcon, match: (p) => p.startsWith("/curriculum") },
    { label: "학습", path: "/study/3", icon: BookIcon, match: (p) => p.startsWith("/study") },
    { label: "퀴즈", path: "/quiz/3", icon: QuizIcon, match: (p) => p.startsWith("/quiz") },
  ];

  return (
    <>
      <header className="sticky top-0 z-50 flex h-[62px] items-center gap-4 border-b border-[#eee] bg-white/90 px-4 backdrop-blur-lg sm:h-[68px] sm:px-8 lg:gap-5">
        <button type="button" className="shrink-0 cursor-pointer border-0 bg-transparent p-0" onClick={() => navigate("/")} aria-label="처음으로">
          <Logo />
        </button>
        <nav className="hidden items-center gap-1 lg:flex" aria-label="주요 페이지">
          {links.map((link) => (
            <NavButton key={link.path} onClick={() => navigate(link.path)}>
              {link.label}
            </NavButton>
          ))}
        </nav>
        <div className="flex-1" />
        {/* 남은 시간 왼쪽에 세션 코드. 넓은 화면에서만 나온다 — 좁은 화면은 본문 카드가 맡는다. */}
        <SessionCodePill />
        <div className="flex items-center gap-2 rounded-full border border-[#FFE0C4] bg-[#FFF3E8] px-3 py-1.5 sm:gap-2.5 sm:px-4 sm:py-2">
          <span className="relative inline-flex size-2" aria-hidden="true">
            <span className="absolute inset-0 animate-pulse-ring rounded-full bg-[#FF7A00]" />
            <span className="absolute inset-0 rounded-full bg-[#FF7A00]" />
          </span>
          <span className="hidden text-xs text-[#666] sm:inline">시험까지</span>
          <span className="text-[13px] font-bold sm:text-[15px]"><Countdown compact /></span>
        </div>
      </header>

      {/* 좁은 화면 — 아래쪽 이동 막대. 넓은 화면에서는 위쪽 nav 가 그 일을 한다. */}
      <nav
        aria-label="주요 페이지"
        className="fixed inset-x-0 bottom-0 z-50 flex h-[var(--bottom-nav-h)] items-stretch border-t border-[#eee] bg-white/95 backdrop-blur-lg lg:hidden"
      >
        {links.map((link) => {
          const Icon = link.icon;
          const active = link.match(pathname);
          return (
            <button
              key={link.path}
              type="button"
              onClick={() => navigate(link.path)}
              aria-label={link.label}
              aria-current={active ? "page" : undefined}
              className={`flex flex-1 cursor-pointer items-center justify-center border-0 bg-transparent px-1 transition-colors ${
                active ? "text-[#E85D00]" : "text-[#999] active:text-[#E85D00]"
              }`}
            >
              <Icon size={23} />
            </button>
          );
        })}
      </nav>
    </>
  );
}

function NavButton({ children, onClick }: { children: React.ReactNode; onClick: () => void }) {
  return (
    <button
      type="button"
      className="cursor-pointer rounded-lg border-0 bg-transparent px-[13px] py-[7px] text-[13px] font-medium text-[#666] transition-colors hover:bg-[#FFF3E8] hover:text-[#E85D00]"
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
      className={`cursor-pointer rounded-xl border border-[#eee] bg-white px-6 py-4 text-[15px] text-[#666] transition-colors hover:border-[#FFE0C4] hover:text-[#E85D00] ${className}`}
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

/*
 * 아이콘은 전부 직접 그린다.
 *
 * 이모지는 글꼴에 따라 모양도 크기도 제각각이고, 어떤 환경에서는 아예 네모로 뜬다.
 * 색을 맞출 수도 없다. 화면에 쓰는 기호는 이 파일의 SVG 로만 둔다.
 */

/** 말풍선. 학습 도우미를 여는 버튼에 쓴다. */
export function ChatIcon({ size = 22, color = "currentColor" }: { size?: number; color?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" className="shrink-0">
      <path
        d="M21 11.5c0 4.14-4.03 7.5-9 7.5-1.02 0-2-.14-2.9-.4L4 20.5l1.2-3.35C3.83 15.82 3 13.76 3 11.5 3 7.36 7.03 4 12 4s9 3.36 9 7.5Z"
        stroke={color}
        strokeWidth="1.9"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/** 닫기. 텍스트 ✕ 대신 쓴다. */
export function CloseIcon({ size = 18, color = "currentColor" }: { size?: number; color?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" className="shrink-0">
      <path d="M6 6l12 12M18 6L6 18" stroke={color} strokeWidth="2.2" strokeLinecap="round" />
    </svg>
  );
}

/** 아래를 가리키는 꺾쇠. 텍스트 ⌄ 대신 쓴다. */
export function ChevronDown({ size = 14, color = "currentColor" }: { size?: number; color?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" className="shrink-0">
      <path d="M6 9l6 6 6-6" stroke={color} strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/*
 * 아래 이동 막대의 아이콘.
 *
 * 좁은 화면에서는 다섯 칸에 글씨를 넣으면 "시험 정보" 같은 말이 줄바꿈되거나 잘린다.
 * 모양으로 구분하고, 무엇인지는 aria-label 이 말한다.
 *
 * 선 두께와 viewBox 를 통일해야 나란히 놓았을 때 굵기가 들쭉날쭉해 보이지 않는다.
 */

type NavIconProps = { size?: number; color?: string };

const NAV_STROKE = 1.8;

/** 집. 처음으로. */
export function HomeIcon({ size = 22, color = "currentColor" }: NavIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" className="shrink-0">
      <path
        d="M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4v-5H9v5H5a1 1 0 0 1-1-1v-8.5Z"
        stroke={color}
        strokeWidth={NAV_STROKE}
        strokeLinejoin="round"
      />
    </svg>
  );
}

/** 달력. 시험 정보. */
export function CalendarIcon({ size = 22, color = "currentColor" }: NavIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" className="shrink-0">
      <rect x="3.5" y="5.5" width="17" height="15" rx="2.5" stroke={color} strokeWidth={NAV_STROKE} />
      <path d="M3.5 10h17M8 3.5v4M16 3.5v4" stroke={color} strokeWidth={NAV_STROKE} strokeLinecap="round" />
    </svg>
  );
}

/** 길과 깃발. 플랜 맵. */
export function MapIcon({ size = 22, color = "currentColor" }: NavIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" className="shrink-0">
      <path
        d="M4 20c0-3 4-3 4-6s-4-3-4-6 5-3 8-3"
        stroke={color}
        strokeWidth={NAV_STROKE}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M16 4v9" stroke={color} strokeWidth={NAV_STROKE} strokeLinecap="round" />
      <path d="M16 4.5h4.5L19 7l1.5 2.5H16" stroke={color} strokeWidth={NAV_STROKE} strokeLinejoin="round" />
    </svg>
  );
}

/** 펼친 책. 학습. */
export function BookIcon({ size = 22, color = "currentColor" }: NavIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" className="shrink-0">
      <path
        d="M12 7.5C10.5 6 8.5 5.5 4 5.5v12c4.5 0 6.5.5 8 2 1.5-1.5 3.5-2 8-2v-12c-4.5 0-6.5.5-8 2Z"
        stroke={color}
        strokeWidth={NAV_STROKE}
        strokeLinejoin="round"
      />
      <path d="M12 7.5v12" stroke={color} strokeWidth={NAV_STROKE} strokeLinecap="round" />
    </svg>
  );
}

/** 물음표. 퀴즈. */
export function QuizIcon({ size = 22, color = "currentColor" }: NavIconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" className="shrink-0">
      <circle cx="12" cy="12" r="8.5" stroke={color} strokeWidth={NAV_STROKE} />
      <path
        d="M9.7 9.6a2.4 2.4 0 0 1 4.6.9c0 1.6-2.3 1.9-2.3 3.4"
        stroke={color}
        strokeWidth={NAV_STROKE}
        strokeLinecap="round"
      />
      <circle cx="12" cy="16.6" r="1" fill={color} />
    </svg>
  );
}

/** 문서. 자료 기반 표시에 쓴다. */
export function DocIcon({ size = 13, color = "currentColor" }: { size?: number; color?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" className="shrink-0">
      <path
        d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5Z"
        stroke={color}
        strokeWidth="1.9"
        strokeLinejoin="round"
      />
      <path d="M14 3v5h5" stroke={color} strokeWidth="1.9" strokeLinejoin="round" />
    </svg>
  );
}

/** 전구. 일반 지식 기반 표시에 쓴다. */
export function BulbIcon({ size = 13, color = "currentColor" }: { size?: number; color?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" className="shrink-0">
      <path
        d="M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.4.3.5.7.5 1.1h6c0-.4.1-.8.5-1.1A6 6 0 0 0 12 3Z"
        stroke={color}
        strokeWidth="1.9"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
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

/**
 * 학습 내용이 무엇에 근거해 만들어졌는지 알리는 표시.
 *
 * <p>자료를 올리지 않으면 서버가 과목명과 시험 범위만 보고 만든다. 그 사실을 감추면
 * 사용자는 자기 강의자료에서 뽑은 내용이라고 믿고 그대로 외운다.
 *
 * <p>근거를 아직 모르는 상태(분석 전)에서는 아무것도 그리지 않는다.
 */
export function SourceBadge({ source }: { source: SourceLabel | null }) {
  if (!source) return null;
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[12px] font-bold ${
        source.grounded ? "bg-[#FFF3E8] text-[#E85D00]" : "bg-[#EEF2FF] text-[#4C51BF]"
      }`}
    >
      {source.grounded ? <DocIcon /> : <BulbIcon />}
      {source.label}
    </span>
  );
}

/** 일반 지식으로 만들었을 때의 안내. 자료 기반이면 아무것도 그리지 않는다. */
export function SourceNotice({ source, className = "" }: { source: SourceLabel | null; className?: string }) {
  if (!source?.notice) return null;
  return (
    <p className={`rounded-xl border border-[#D7DDFF] bg-[#F5F7FF] px-4 py-3 text-[13px] leading-[1.7] text-[#3C3F8F] ${className}`}>
      {source.notice}
    </p>
  );
}
