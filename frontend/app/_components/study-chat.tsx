"use client";

import { useEffect, useRef, useState } from "react";
import { Ghost } from "@/app/_components/ui";
import { useStudyChat, type StudyChatView } from "@/app/_components/use-study-chat";

/**
 * 학습 챗봇.
 *
 * <p>넓은 화면에서는 퀴즈 옆에 붙은 사이드바로, 좁은 화면에서는 떠 있는 말풍선 버튼과
 * 아래에서 올라오는 시트로 나타난다. <b>둘은 같은 대화를 본다.</b> 상태를 각각 갖게 하면
 * 창 크기를 바꾸는 순간 한쪽 대화가 사라진 것처럼 보인다.
 *
 * <p>열기만 해서는 AI 를 부르지 않는다. 보내기를 누른 순간에만 부른다.
 */
export function StudyChat({ variant = "sidebar" }: { variant?: "sidebar" | "floating" }) {
  const [openAsSheet, setOpenAsSheet] = useState(false);
  const chat = useStudyChat(true);

  // 이미 오른쪽 칸을 쓰고 있는 화면(학습 화면)에서는 사이드바를 붙일 자리가 없다.
  // 그때는 어느 너비에서든 떠 있는 버튼으로 둔다.
  const floatingOnly = variant === "floating";
  const hideOnWide = floatingOnly ? "" : " lg:hidden";

  return (
    <>
      {/* 넓은 화면 — 퀴즈 옆에 붙는다 */}
      {!floatingOnly && (
        <aside className="hidden lg:sticky lg:top-6 lg:flex lg:h-[calc(100vh-8rem)] lg:flex-col lg:overflow-hidden lg:rounded-[18px] lg:border lg:border-[#eee]">
          <ChatPanel chat={chat} />
        </aside>
      )}

      {!openAsSheet && (
        <button
          type="button"
          onClick={() => setOpenAsSheet(true)}
          aria-label="학습 도우미에게 질문하기"
          className={`fixed bottom-5 right-5 z-40 flex items-center gap-2 rounded-full border-0 bg-[#FF7A00] px-5 py-3.5 text-[14.5px] font-bold text-white shadow-[0_10px_24px_rgba(255,122,0,.4)] transition-colors hover:bg-[#E85D00]${hideOnWide}`}
        >
          <span aria-hidden="true">💬</span>
          모르는 게 있어요
        </button>
      )}

      {/* 아래에서 올라오는 시트 */}
      {openAsSheet && (
        <div
          className={`fixed inset-0 z-50 flex flex-col justify-end${hideOnWide}`}
          role="dialog"
          aria-modal="true"
        >
          <button
            type="button"
            aria-label="닫기"
            onClick={() => setOpenAsSheet(false)}
            className="flex-1 cursor-default border-0 bg-black/40"
          />
          <div className="flex h-[78vh] flex-col overflow-hidden rounded-t-[22px] bg-white shadow-[0_-8px_30px_rgba(0,0,0,.18)] sm:mx-auto sm:w-full sm:max-w-[520px] sm:rounded-t-[22px]">
            <ChatPanel chat={chat} onClose={() => setOpenAsSheet(false)} />
          </div>
        </div>
      )}
    </>
  );
}

function ChatPanel({ chat, onClose }: { chat: StudyChatView; onClose?: () => void }) {
  const [draft, setDraft] = useState("");
  const listRef = useRef<HTMLDivElement>(null);

  // 새 말풍선이 생기면 아래로 붙인다. 답이 왔는데 위쪽만 보이면 온 줄 모른다.
  useEffect(() => {
    const list = listRef.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [chat.messages.length, chat.sending]);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    const question = draft.trim();
    if (!question || chat.sending) return;
    setDraft("");
    void chat.send(question);
  };

  return (
    <>
      <header className="flex shrink-0 items-center gap-2.5 border-b border-[#eee] px-4 py-3.5">
        <Ghost width={30} mood="smile" />
        <div className="min-w-0 flex-1">
          <div className="text-[14px] font-bold">학습 도우미</div>
          <div className="truncate text-[11.5px] text-[#888]">
            {chat.grounded ? "올려주신 자료를 보고 답해요" : "일반적인 교과 지식으로 답해요"}
          </div>
        </div>
        {onClose && (
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="cursor-pointer rounded-lg border-0 bg-transparent px-2 py-1 text-[18px] text-[#888]"
          >
            ✕
          </button>
        )}
      </header>

      <div ref={listRef} className="flex-1 overflow-y-auto px-4 py-4">
        {chat.loading && <p className="text-[13px] text-[#888]">지난 대화를 불러오는 중…</p>}

        {!chat.loading && chat.messages.length === 0 && (
          <div className="rounded-xl bg-[#FFF3E8] px-4 py-3.5 text-[13px] leading-[1.75] text-[#7A4A16]">
            공부하다 막히는 게 있으면 물어보세요.
            <br />
            {chat.grounded
              ? "올려주신 자료에서 찾아 답할게요."
              : "자료를 올리지 않으셔서 일반적인 교과 지식으로 답해요."}
          </div>
        )}

        <div className="grid gap-3">
          {chat.messages.map((message, index) => (
            <div
              key={`${message.createdAt}-${index}`}
              className={message.role === "USER" ? "flex justify-end" : "flex justify-start"}
            >
              <p
                className={`max-w-[85%] whitespace-pre-wrap rounded-2xl px-3.5 py-2.5 text-[13.5px] leading-[1.7] ${
                  message.role === "USER"
                    ? "rounded-br-md bg-[#FF7A00] text-white"
                    : "rounded-bl-md bg-[#F6F6F6] text-[#222]"
                }`}
              >
                {message.content}
              </p>
            </div>
          ))}

          {chat.sending && (
            <div className="flex justify-start">
              <p className="rounded-2xl rounded-bl-md bg-[#F6F6F6] px-3.5 py-2.5 text-[13.5px] text-[#888]">
                <span className="animate-pulse">생각하는 중…</span>
              </p>
            </div>
          )}
        </div>

        {/*
          자료가 있는데 그 질문의 근거를 자료에서 못 찾은 경우. 감추면 사용자는
          방금 읽은 답이 자기 강의자료에 있는 내용이라고 믿는다.
        */}
        {chat.lastAnswerOutsideMaterial && !chat.sending && (
          <p className="mt-3 rounded-xl border border-[#D7DDFF] bg-[#F5F7FF] px-3.5 py-2.5 text-[12px] leading-[1.7] text-[#3C3F8F]">
            이 답변은 올려주신 자료에서 근거를 찾지 못해 일반적인 교과 지식으로 답한 내용이에요.
          </p>
        )}

        {chat.error && (
          <p role="alert" className="mt-3 rounded-xl border border-[#F5C2C7] bg-[#FDECEE] px-3.5 py-2.5 text-[12.5px] text-[#B02A37]">
            {chat.error}
          </p>
        )}
      </div>

      <form onSubmit={submit} className="shrink-0 border-t border-[#eee] p-3">
        <div className="flex items-end gap-2">
          <textarea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              // Enter 로 보내고 Shift+Enter 로 줄바꿈. 채팅 입력의 관습이다.
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                submit(event);
              }
            }}
            placeholder="ex ) 교착상태 조건이 뭐예요?"
            rows={1}
            maxLength={1000}
            aria-label="질문 입력"
            className="form-input max-h-28 min-h-[44px] flex-1 resize-none py-2.5 text-[13.5px]"
          />
          <button
            type="submit"
            disabled={chat.sending || !draft.trim()}
            className="h-[44px] shrink-0 cursor-pointer rounded-xl border-0 bg-[#FF7A00] px-4 text-[13.5px] font-bold text-white transition-colors hover:bg-[#E85D00] disabled:cursor-not-allowed disabled:bg-[#FFD2AC]"
          >
            {chat.sending ? "…" : "보내기"}
          </button>
        </div>
      </form>
    </>
  );
}
