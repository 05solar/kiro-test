"use client";

import { useSyncExternalStore } from "react";

const emptySubscribe = () => () => {};

/**
 * persist된 store 값을 읽는 컴포넌트에서 SSR/CSR hydration mismatch를 막기 위한 공용 훅.
 * useSyncExternalStore로 서버 스냅샷은 false, 클라이언트 마운트 이후에는 true를 반환한다.
 * (effect 내 setState 없이 하이드레이션 여부를 판별)
 */
export function useHydrated() {
  return useSyncExternalStore(
    emptySubscribe,
    () => true,
    () => false
  );
}
