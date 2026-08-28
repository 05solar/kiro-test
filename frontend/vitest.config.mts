import { defineConfig } from "vitest/config";
import path from "node:path";

/**
 * 순수 로직만 테스트한다.
 *
 * 대상은 API 클라이언트와 어댑터다. 이 둘이 백엔드와 화면 사이의 접합면이고,
 * 여기가 어긋나면 화면에 조용히 틀린 값이 나온다.
 *
 * 실제 네트워크를 부르지 않는다. fetch 를 갈아 끼워 응답을 직접 만든다.
 * AI 호출은 과금되므로 자동 테스트에서 절대 부르지 않는다.
 */
export default defineConfig({
  resolve: {
    alias: { "@": path.resolve(__dirname, ".") },
  },
  test: {
    environment: "node",
    include: ["lib/**/*.test.ts"],
  },
});
