"use client";

import { useCallback, useEffect, useState } from "react";
import { getSession, toMessage, type SessionResponse } from "@/lib/api";
import { toSourceLabel, type SourceLabel } from "@/lib/api/adapt";
import { useSessionStore } from "./session-store";

/**
 * 서버 세션을 읽는다.
 *
 * <p>과목명·시험 범위·남은 시간, 그리고 <b>무엇에 근거해 만들었는지</b>가 여기 있다.
 * 화면이 로컬 스토어에 남겨 둔 값은 입력 프리필용일 뿐이라, 다른 기기에서 이어하면
 * 비어 있다. 표시에 쓰는 값은 서버에서 읽는다.
 *
 * <p>AI 를 부르지 않는 단순 조회다. 화면을 열 때마다 불러도 과금되지 않는다.
 */
export type SessionView = {
  loading: boolean;
  error: string | null;
  session: SessionResponse | null;
  /** 자료 기반인지 일반 지식 기반인지. 분석 전에는 null. */
  source: SourceLabel | null;
  reload: () => Promise<void>;
};

export function useSession(): SessionView {
  const sessionCode = useSessionStore((state) => state.sessionCode);
  const [session, setSession] = useState<SessionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  /** 화면이 직접 부르는 다시 읽기. 첫 조회는 아래 effect 가 한다. */
  const load = useCallback(async () => {
    if (!sessionCode) return;
    setLoading(true);
    try {
      setSession(await getSession(sessionCode));
      setError(null);
    } catch (e) {
      // 조회 실패로 화면을 막지 않는다. 표시용 정보일 뿐이다.
      setError(toMessage(e));
    } finally {
      setLoading(false);
    }
  }, [sessionCode]);

  useEffect(() => {
    // 세션 코드가 없으면 읽을 것이 없다. 아래 반환값에서 비어 있는 상태로 내보낸다.
    if (!sessionCode) return;
    let active = true;
    getSession(sessionCode)
      .then((found) => {
        if (!active) return;
        setSession(found);
        setError(null);
      })
      .catch((e) => {
        if (active) setError(toMessage(e));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [sessionCode]);

  // 세션 코드가 없는 상태는 "읽는 중"이 아니라 "읽을 것이 없는" 상태다.
  // 상태로 들고 있지 않고 여기서 계산한다 — effect 안에서 곧바로 상태를 바꾸면
  // 바뀌는 것 없이 렌더만 한 번 더 돈다.
  const hasSession = Boolean(sessionCode);
  return {
    loading: hasSession && loading,
    error,
    session: hasSession ? session : null,
    source: hasSession ? toSourceLabel(session) : null,
    reload: load,
  };
}
