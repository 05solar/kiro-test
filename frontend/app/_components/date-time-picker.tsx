"use client";

import { useEffect, useRef, useState, type RefObject } from "react";

/**
 * 시험 날짜·시간 전용 커스텀 선택기.
 *
 * 브라우저 기본 date/time 입력을 쓰지 않는 이유:
 * - 과거 날짜가 그대로 보인다. 벼락치기 도구에서 과거 시험 날짜는 선택지가 아니다.
 * - 분 단위가 자유 입력이라 "정각/30분" 규칙을 강제할 수 없다.
 * - 브라우저마다 생김새가 달라 앱의 동글동글한 디자인과 어긋난다.
 *
 * 값의 형식은 기존 스토어 계약을 그대로 따른다 — 날짜 "YYYY-MM-DD", 시간 "HH:MM".
 */

/** 오늘부터 이 날수 뒤까지만 고를 수 있다. 벼락치기의 시야는 일주일이면 충분하다. */
const DAYS_AHEAD = 7;

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"] as const;

function toDateValue(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** 오늘부터 DAYS_AHEAD 일 뒤까지, 고를 수 있는 날짜만 만든다. 과거는 아예 만들지 않는다. */
function selectableDates(): { value: string; month: number; day: number; weekday: string; offset: number }[] {
  const today = new Date();
  return Array.from({ length: DAYS_AHEAD + 1 }, (_, offset) => {
    const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() + offset);
    return {
      value: toDateValue(date),
      month: date.getMonth() + 1,
      day: date.getDate(),
      weekday: WEEKDAYS[date.getDay()],
      offset,
    };
  });
}

function formatDateLabel(value: string): string {
  const [, month, day] = value.split("-").map(Number);
  const date = new Date(Number(value.slice(0, 4)), month - 1, day);
  return `${month}월 ${day}일 (${WEEKDAYS[date.getDay()]})`;
}

function formatTimeLabel(value: string): string {
  const [hour, minute] = value.split(":").map(Number);
  const meridiem = hour < 12 ? "오전" : "오후";
  const displayHour = hour % 12 === 0 ? 12 : hour % 12;
  return `${meridiem} ${displayHour}시${minute === 30 ? " 30분" : ""}`;
}

/** 트리거 버튼 + 팝오버 골격. 바깥을 누르면 닫힌다. */
function usePopover() {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: PointerEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  return { open, setOpen, rootRef };
}

type FieldProps = {
  value: string;
  onChange: (value: string) => void;
  invalid?: boolean;
  /** 필수 검증에서 스크롤·포커스 대상으로 쓰는 트리거 버튼 ref. */
  triggerRef?: RefObject<HTMLButtonElement | null>;
};

function triggerClass(invalid: boolean, placeholder: boolean): string {
  return [
    "form-input flex w-full cursor-pointer items-center justify-between rounded-full text-left",
    invalid ? "form-input-error" : "",
    placeholder ? "text-[#9a9a9a]" : "",
  ].join(" ");
}

function ChevronDown({ open }: { open: boolean }) {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
      className={`shrink-0 text-[#9a9a9a] transition-transform ${open ? "rotate-180" : ""}`}
    >
      <path d="m5 9 7 7 7-7" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/** 시험 날짜 선택. 오늘부터 일주일 뒤까지의 날짜 알약만 보여준다. */
export function DatePickerField({ value, onChange, invalid = false, triggerRef }: FieldProps) {
  const { open, setOpen, rootRef } = usePopover();
  const dates = selectableDates();
  // 저장돼 있던 값이 선택 가능 범위를 벗어났으면(어제 고른 오늘 이전 날짜 등) 비어 있는 것으로 본다.
  const selected = dates.find((date) => date.value === value) ?? null;

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        ref={triggerRef}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-invalid={invalid}
        onClick={() => setOpen((current) => !current)}
        className={triggerClass(invalid, !selected)}
      >
        <span>{selected ? formatDateLabel(selected.value) : "날짜 선택"}</span>
        <ChevronDown open={open} />
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="시험 날짜 선택"
          className="absolute left-0 top-[calc(100%+8px)] z-40 w-full min-w-[300px] rounded-[22px] border border-[#FFE0C4] bg-white p-4 shadow-[0_14px_36px_rgba(255,122,0,.18)]"
        >
          <div className="mb-3 px-1 text-[12.5px] font-bold text-[#666]">
            오늘부터 일주일 안에서 골라 주세요
          </div>
          <div className="grid grid-cols-4 gap-2">
            {dates.map((date) => {
              const isSelected = date.value === value;
              return (
                <button
                  key={date.value}
                  type="button"
                  onClick={() => {
                    onChange(date.value);
                    setOpen(false);
                  }}
                  className={`flex cursor-pointer flex-col items-center gap-0.5 rounded-2xl border px-2 py-2.5 transition-colors ${
                    isSelected
                      ? "border-[#FF7A00] bg-[#FF7A00] text-white"
                      : "border-[#f2e4d5] bg-[#FFFDFB] text-[#555] hover:border-[#FFD3A8] hover:bg-[#FFF3E8]"
                  }`}
                >
                  <span className={`text-[11px] ${isSelected ? "text-white/85" : date.weekday === "일" ? "text-[#E03131]" : "text-[#8a8a8a]"}`}>
                    {date.offset === 0 ? "오늘" : date.offset === 1 ? "내일" : date.weekday}
                  </span>
                  <span className="font-jua text-[17px] leading-none">{date.day}</span>
                  <span className={`text-[10.5px] ${isSelected ? "text-white/85" : "text-[#9a9a9a]"}`}>{date.month}월</span>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

/*
 * 시험 시간 목록.
 *
 * 분은 5분 단위다. 시험이 13:47 에 시작하는 일은 없지만 9:50 은 있다.
 * 예전에는 정각·30분 둘뿐이었는데, 휠로 바꾸면서 그 정도는 열어 뒀다.
 */
const HOURS = Array.from({ length: 24 }, (_, hour) => String(hour).padStart(2, "0"));
const MINUTES = Array.from({ length: 12 }, (_, index) => String(index * 5).padStart(2, "0"));

/** 휠 한 칸의 높이(px). 스크롤 위치를 인덱스로 바꾸는 계산이 전부 이 값을 기준으로 한다. */
const ITEM_H = 40;
/** 위아래로 보이는 칸 수. 가운데 한 칸을 중심으로 양쪽에 이만큼씩 보인다. */
const VISIBLE_SIDE = 2;

/**
 * 세로로 굴려서 고르는 한 칸.
 *
 * <p>스크롤이 멈춘 자리의 가운데 항목이 곧 선택이다. 손가락으로 굴리다 멈추면 그대로
 * 골라지고, 따로 확인을 누를 필요가 없다.
 *
 * <p>스크롤만으로는 키보드를 쓸 수 없어 위/아래 키도 함께 받는다.
 */
function WheelColumn({
  items,
  value,
  onChange,
  label,
}: {
  items: readonly string[];
  value: string | null;
  onChange: (next: string) => void;
  label: string;
}) {
  const listRef = useRef<HTMLDivElement>(null);
  const settleRef = useRef<number | undefined>(undefined);
  /*
   * 값이 바뀌어 스크롤을 옮기는 중인지.
   *
   * 이 표시가 없으면 "선택 → 스크롤 이동 → 스크롤 이벤트 → 선택" 이 돌아 값이 튄다.
   */
  const movingRef = useRef(false);

  const index = Math.max(0, items.indexOf(value ?? ""));

  // 선택이 바뀌면 그 자리로 굴려 놓는다. 처음 열었을 때 가운데를 맞추는 것도 이 일이다.
  useEffect(() => {
    const list = listRef.current;
    if (!list) return;
    const top = index * ITEM_H;
    if (Math.abs(list.scrollTop - top) < 1) return;
    movingRef.current = true;
    list.scrollTo({ top, behavior: "smooth" });
    const id = window.setTimeout(() => {
      movingRef.current = false;
    }, 320);
    return () => window.clearTimeout(id);
  }, [index]);

  const handleScroll = () => {
    if (movingRef.current) return;
    window.clearTimeout(settleRef.current);
    // 굴리는 동안에는 고르지 않는다. 멈춘 뒤의 자리만 본다.
    settleRef.current = window.setTimeout(() => {
      const list = listRef.current;
      if (!list) return;
      const next = items[Math.round(list.scrollTop / ITEM_H)];
      if (next && next !== value) onChange(next);
    }, 110);
  };

  const move = (delta: number) => {
    const next = items[Math.min(items.length - 1, Math.max(0, index + delta))];
    if (next) onChange(next);
  };

  return (
    <div className="flex-1">
      <div className="mb-2 text-center text-[12px] font-bold text-[#666]">{label}</div>
      <div
        className="relative"
        style={{ height: ITEM_H * (VISIBLE_SIDE * 2 + 1) }}
      >
        {/* 가운데 선택 자리 표시. 스크롤과 함께 움직이지 않도록 바깥에 둔다. */}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-0 z-10 rounded-xl border-y border-[#FFD3A8] bg-[#FFF3E8]/70"
          style={{ top: ITEM_H * VISIBLE_SIDE, height: ITEM_H }}
        />
        <div
          ref={listRef}
          onScroll={handleScroll}
          onKeyDown={(event) => {
            if (event.key === "ArrowDown") {
              event.preventDefault();
              move(1);
            } else if (event.key === "ArrowUp") {
              event.preventDefault();
              move(-1);
            }
          }}
          tabIndex={0}
          role="listbox"
          aria-label={label}
          aria-activedescendant={value ? `${label}-${value}` : undefined}
          className="h-full snap-y snap-mandatory overflow-y-auto scroll-smooth outline-none [-ms-overflow-style:none] [scrollbar-width:none] focus-visible:ring-2 focus-visible:ring-[#FFD3A8] [&::-webkit-scrollbar]:hidden"
          // 첫 항목과 마지막 항목도 가운데에 올 수 있어야 한다.
          style={{ paddingTop: ITEM_H * VISIBLE_SIDE, paddingBottom: ITEM_H * VISIBLE_SIDE }}
        >
          {items.map((item) => {
            const selected = item === value;
            return (
              <div
                key={item}
                id={`${label}-${item}`}
                role="option"
                aria-selected={selected}
                onClick={() => onChange(item)}
                className={`flex cursor-pointer snap-center items-center justify-center text-[17px] tabular-nums transition-colors ${
                  selected ? "font-bold text-[#E85D00]" : "text-[#b0b0b0]"
                }`}
                style={{ height: ITEM_H }}
              >
                {item}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

/** 시험 시간 선택. 시와 분을 각각 굴려서 고른다. */
export function TimePickerField({ value, onChange, invalid = false, triggerRef }: FieldProps) {
  const { open, setOpen, rootRef } = usePopover();
  const [selectedHour, selectedMinute] = value ? value.split(":") : [null, null];

  /*
   * 굴리기 시작하면 곧바로 값이 생긴다.
   *
   * 아직 아무것도 안 골랐다면 한쪽만 굴려도 나머지는 기본값으로 채운다 —
   * 시만 맞추고 닫아도 시각이 성립해야 한다.
   */
  const pickHour = (hour: string) => onChange(`${hour}:${selectedMinute ?? "00"}`);
  const pickMinute = (minute: string) => onChange(`${selectedHour ?? "09"}:${minute}`);

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        ref={triggerRef}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-invalid={invalid}
        onClick={() => setOpen((current) => !current)}
        className={triggerClass(invalid, !value)}
      >
        <span>{value ? formatTimeLabel(value) : "시간 선택"}</span>
        <ChevronDown open={open} />
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="시험 시간 선택"
          className="absolute left-0 top-[calc(100%+8px)] z-40 w-full min-w-[300px] rounded-[22px] border border-[#FFE0C4] bg-white p-4 shadow-[0_14px_36px_rgba(255,122,0,.18)]"
        >
          <div className="flex items-start gap-2">
            <WheelColumn label="시" items={HOURS} value={selectedHour} onChange={pickHour} />
            <div
              aria-hidden="true"
              className="flex shrink-0 items-center justify-center pt-[26px] text-[18px] font-bold text-[#ccc]"
              style={{ height: ITEM_H * (VISIBLE_SIDE * 2 + 1) }}
            >
              :
            </div>
            <WheelColumn label="분" items={MINUTES} value={selectedMinute} onChange={pickMinute} />
          </div>

          <button
            type="button"
            onClick={() => setOpen(false)}
            disabled={!value}
            className="mt-3 w-full cursor-pointer rounded-xl border-0 bg-[#FF7A00] py-3 text-[14px] font-bold text-white transition-colors hover:bg-[#E85D00] disabled:cursor-not-allowed disabled:bg-[#FFD2AC]"
          >
            {value ? `${formatTimeLabel(value)} 로 선택` : "시간을 굴려서 골라 주세요"}
          </button>
        </div>
      )}
    </div>
  );
}
