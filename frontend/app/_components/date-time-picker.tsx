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
    placeholder ? "text-[#B5B5B5]" : "",
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
      className={`shrink-0 text-[#B5B5B5] transition-transform ${open ? "rotate-180" : ""}`}
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
          <div className="mb-3 px-1 text-[12.5px] font-bold text-[#888]">
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
                  <span className={`text-[11px] ${isSelected ? "text-white/85" : date.weekday === "일" ? "text-[#E03131]" : "text-[#aaa]"}`}>
                    {date.offset === 0 ? "오늘" : date.offset === 1 ? "내일" : date.weekday}
                  </span>
                  <span className="font-jua text-[17px] leading-none">{date.day}</span>
                  <span className={`text-[10.5px] ${isSelected ? "text-white/85" : "text-[#bbb]"}`}>{date.month}월</span>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

/** 정각·30분만 고를 수 있게 만든 시간 목록. */
const MINUTES = ["00", "30"] as const;
const HOURS = Array.from({ length: 24 }, (_, hour) => hour);

/** 시험 시간 선택. 분은 정각/30분만 허용한다 — 시험이 13:47 에 시작하는 일은 없다. */
export function TimePickerField({ value, onChange, invalid = false, triggerRef }: FieldProps) {
  const { open, setOpen, rootRef } = usePopover();
  const [selectedHour, selectedMinute] = value ? value.split(":") : [null, null];

  const pickHour = (hour: number) => {
    const hh = String(hour).padStart(2, "0");
    // 분을 아직 안 골랐으면 정각으로 시작한다. 시만 고르고 닫아도 값이 성립한다.
    onChange(`${hh}:${selectedMinute ?? "00"}`);
  };

  const pickMinute = (minute: (typeof MINUTES)[number]) => {
    if (selectedHour === null) return;
    onChange(`${selectedHour}:${minute}`);
    setOpen(false);
  };

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
          <div className="mb-2.5 px-1 text-[12.5px] font-bold text-[#888]">시</div>
          <div className="mb-4 grid grid-cols-6 gap-1.5">
            {HOURS.map((hour) => {
              const hh = String(hour).padStart(2, "0");
              const isSelected = selectedHour === hh;
              return (
                <button
                  key={hour}
                  type="button"
                  onClick={() => pickHour(hour)}
                  className={`cursor-pointer rounded-full border py-2 text-[13px] tabular-nums transition-colors ${
                    isSelected
                      ? "border-[#FF7A00] bg-[#FF7A00] font-bold text-white"
                      : "border-[#f2e4d5] bg-[#FFFDFB] text-[#555] hover:border-[#FFD3A8] hover:bg-[#FFF3E8]"
                  }`}
                >
                  {hour}
                </button>
              );
            })}
          </div>
          <div className="mb-2.5 px-1 text-[12.5px] font-bold text-[#888]">분 · 정각과 30분만 가능해요</div>
          <div className="grid grid-cols-2 gap-2">
            {MINUTES.map((minute) => {
              const isSelected = value !== "" && selectedMinute === minute;
              const disabled = selectedHour === null;
              return (
                <button
                  key={minute}
                  type="button"
                  disabled={disabled}
                  onClick={() => pickMinute(minute)}
                  className={`rounded-full border py-2.5 text-[14px] font-bold transition-colors ${
                    isSelected
                      ? "border-[#FF7A00] bg-[#FF7A00] text-white"
                      : disabled
                        ? "cursor-not-allowed border-[#f2e4d5] bg-[#fafafa] text-[#ccc]"
                        : "cursor-pointer border-[#f2e4d5] bg-[#FFFDFB] text-[#555] hover:border-[#FFD3A8] hover:bg-[#FFF3E8]"
                  }`}
                >
                  {minute}분
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
