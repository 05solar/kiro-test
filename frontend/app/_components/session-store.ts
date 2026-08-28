"use client";

import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

/**
 * 서버 세션과의 연결.
 *
 * <p>8자리 코드가 이 서비스의 유일한 접근 키다. 회원가입이 없으므로 이 값을 잃으면
 * 올린 자료도 만든 계획도 되찾을 수 없다. 그래서 localStorage 에 남긴다.
 *
 * <p><b>진행 상태를 여기에 두지 않는다.</b> 완료한 단계, 남은 시간, 배정 시간은 전부
 * 서버가 갖고 있다. 화면이 따로 들고 있으면 다른 기기에서 접속했을 때 두 값이 어긋나고,
 * 그때 어느 쪽이 맞는지 판단할 근거가 없다. 화면은 매번 서버에서 읽는다.
 *
 * <p>여기 담는 것은 <b>서버에 물어보기 전에 알아야 하는 것</b>뿐이다 — 세션 코드와,
 * 사용자가 입력했지만 아직 서버에 보내지 않은 값.
 */

type SessionState = {
  /** 서버가 발급한 8자리 코드. 아직 세션을 만들지 않았으면 null. */
  sessionCode: string | null;
  setSessionCode: (code: string | null) => void;
  clear: () => void;
};

export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      sessionCode: null,
      setSessionCode: (code) => set({ sessionCode: code }),
      clear: () => set({ sessionCode: null }),
    }),
    {
      name: "naeil-session",
      storage: createJSONStorage(() => localStorage),
    }
  )
);
