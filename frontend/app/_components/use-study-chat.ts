"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  askStudyChat,
  getChatHistory,
  toMessage,
  type ChatMessageResponse,
} from "@/lib/api";
import { useSessionStore } from "./session-store";

/**
 * 학습 챗봇 대화 상태.
 *
 * <p><b>대화는 서버가 갖는다.</b> 화면은 읽어 오기만 한다. 새로고침하거나 다른 기기에서
 * 8자리 코드로 들어와도 나눈 대화가 그대로 이어져야 하고, 화면이 따로 들고 있으면 사라진다.
 *
 * <p>보낸 질문은 답이 오기 전에 화면에 먼저 띄운다(낙관적 표시). 서버가 실패하면 그 줄을
 * 되돌린다 — 저장되지 않은 질문이 남아 있으면 다음에 열었을 때 사라져 있다.
 *
 * <p><b>열 때 자동으로 질문하지 않는다.</b> AI 호출은 사용자가 보내기를 누른 순간에만
 * 일어난다. 화면을 열 때마다 부르면 과금이 방문 수만큼 늘어난다.
 */
export type StudyChatView = {
  messages: ChatMessageResponse[];
  /** 서버가 아직 답하지 않은 상태. 입력을 막고 표시를 바꾼다. */
  sending: boolean;
  loading: boolean;
  error: string | null;
  /** 이 세션이 강의자료에 근거하는지. 대화창 머리에 표시한다. */
  grounded: boolean;
  /** 마지막 답변이 자료 밖이었는지. 자료 기반 세션에서만 의미가 있다. */
  lastAnswerOutsideMaterial: boolean;
  send: (message: string) => Promise<void>;
  dismissError: () => void;
};

export function useStudyChat(enabled: boolean): StudyChatView {
  const sessionCode = useSessionStore((state) => state.sessionCode);

  const [messages, setMessages] = useState<ChatMessageResponse[]>([]);
  const [grounded, setGrounded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastAnswerOutsideMaterial, setLastAnswerOutsideMaterial] = useState(false);

  // 대화 기록은 한 번만 읽는다. 열고 닫을 때마다 다시 읽을 이유가 없다(AI 호출은 아니지만
  // 매번 세션 만료도 함께 연장되어 의미 없는 쓰기가 늘어난다).
  const loadedRef = useRef(false);

  useEffect(() => {
    if (!enabled || !sessionCode || loadedRef.current) return;
    loadedRef.current = true;

    let active = true;
    setLoading(true);
    void getChatHistory(sessionCode)
      .then((history) => {
        if (!active) return;
        setMessages(history.messages);
        setGrounded(history.grounded);
      })
      .catch(() => {
        // 기록을 못 읽었다고 대화를 막지 않는다. 새로 물으면 된다.
        loadedRef.current = false;
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [enabled, sessionCode]);

  const send = useCallback(
    async (message: string) => {
      const question = message.trim();
      if (!question || !sessionCode || sending) return;

      const asked: ChatMessageResponse = {
        role: "USER",
        content: question,
        createdAt: new Date().toISOString(),
      };
      setMessages((current) => [...current, asked]);
      setSending(true);
      setError(null);

      try {
        const response = await askStudyChat(sessionCode, question);
        setGrounded(response.grounded);
        setLastAnswerOutsideMaterial(response.grounded && !response.answeredFromMaterial);
        setMessages((current) => [
          ...current,
          { role: "ASSISTANT", content: response.answer, createdAt: response.answeredAt },
        ]);
      } catch (e) {
        // 서버에 저장되지 않은 질문을 화면에만 남겨 두지 않는다.
        setMessages((current) => current.filter((item) => item !== asked));
        setError(toMessage(e));
      } finally {
        setSending(false);
      }
    },
    [sessionCode, sending]
  );

  return {
    messages,
    sending,
    loading,
    error,
    grounded,
    lastAnswerOutsideMaterial,
    send,
    dismissError: () => setError(null),
  };
}
