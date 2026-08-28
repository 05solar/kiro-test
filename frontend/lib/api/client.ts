import type { ErrorResponse } from "./types";

/**
 * 백엔드 호출의 유일한 통로.
 *
 * <p>모든 요청이 여기를 지난다. 페이지마다 `fetch` 를 직접 쓰면 에러 처리와
 * 기본 경로가 흩어지고, 그중 하나만 빠뜨려도 사용자에게 빈 화면이 나간다.
 *
 * <p>주소는 항상 같은 오리진(`/api/...`)이다. 실제 백엔드 전달은
 * `next.config.ts` 의 rewrite 가 서버에서 한다. 그래서 CORS 설정이 필요 없고,
 * 브라우저 번들에 백엔드 주소가 들어가지 않는다.
 */

/** 백엔드가 코드와 함께 돌려준 실패. 화면은 `code` 로 분기하고 `message` 를 그대로 보여준다. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }

  /** 세션이 없거나 만료됐다. 처음 화면으로 돌려보내야 하는 경우. */
  get isSessionGone(): boolean {
    return this.code === "SESSION_NOT_FOUND" || this.code === "INVALID_SESSION_CODE";
  }
}

/** 서버에 닿지 못했다. 백엔드가 꺼져 있거나 네트워크가 끊긴 경우. */
export class NetworkError extends Error {
  constructor(cause: unknown) {
    super("서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    this.name = "NetworkError";
    this.cause = cause;
  }
}

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  /** JSON 본문. `body` 와 함께 쓰지 않는다. */
  json?: unknown;
  /** 파일 업로드용. `Content-Type` 을 직접 넣지 않는다 — 경계 문자열을 브라우저가 만든다. */
  body?: FormData;
  signal?: AbortSignal;
};

const BASE = "/api";

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", json, body, signal } = options;

  const init: RequestInit = { method, signal };
  if (json !== undefined) {
    init.headers = { "Content-Type": "application/json" };
    init.body = JSON.stringify(json);
  } else if (body !== undefined) {
    init.body = body;
  }

  let response: Response;
  try {
    response = await fetch(`${BASE}${path}`, init);
  } catch (cause) {
    // AbortController 로 취소한 경우는 오류가 아니다. 그대로 올려보낸다.
    if (cause instanceof DOMException && cause.name === "AbortError") throw cause;
    throw new NetworkError(cause);
  }

  const text = await response.text();
  const parsed = text ? safeParse(text) : null;

  if (!response.ok) {
    const error = (parsed ?? {}) as Partial<ErrorResponse>;
    throw new ApiError(
      response.status,
      error.code ?? "UNKNOWN",
      error.message ?? "요청을 처리하지 못했습니다."
    );
  }

  return parsed as T;
}

/**
 * 본문이 JSON 이 아닐 수도 있다.
 *
 * <p>프록시나 로드밸런서가 끼어들면 HTML 오류 페이지가 올 수 있는데,
 * 그걸 파싱하다 터지면 원래 실패 원인이 가려진다.
 */
function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/** 화면에 그대로 띄울 수 있는 문구로 바꾼다. 내부 오류 메시지를 노출하지 않는다. */
export function toMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof NetworkError) return error.message;
  return "알 수 없는 오류가 발생했습니다.";
}
