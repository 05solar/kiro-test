import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, NetworkError, request, toMessage } from "./client";

/**
 * API 클라이언트 검증.
 *
 * 실제 네트워크를 부르지 않는다. fetch 를 갈아 끼워 응답을 직접 만든다.
 * AI 를 호출하는 경로는 과금되므로 자동 테스트에서 절대 부르지 않는다.
 */

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function jsonResponse(status: number, body: unknown) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => (body === undefined ? "" : JSON.stringify(body)),
  } as Response;
}

function textResponse(status: number, body: string) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => body,
  } as Response;
}

describe("경로", () => {
  it("항상 같은 오리진의 /api 로 부른다", async () => {
    // 백엔드 주소를 직접 부르면 CORS 에 막힌다. 전달은 Next 의 rewrite 가 한다.
    fetchMock.mockResolvedValue(jsonResponse(200, { ok: true }));
    await request("/sessions/ABCD1234");
    expect(fetchMock.mock.calls[0][0]).toBe("/api/sessions/ABCD1234");
  });
});

describe("요청 본문", () => {
  it("JSON 은 Content-Type 을 붙인다", async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}));
    await request("/x", { method: "PUT", json: { subject: "운영체제" } });

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.method).toBe("PUT");
    expect(init.headers).toEqual({ "Content-Type": "application/json" });
    expect(init.body).toBe(JSON.stringify({ subject: "운영체제" }));
  });

  it("FormData 에는 Content-Type 을 붙이지 않는다", async () => {
    // multipart 경계 문자열은 브라우저가 만든다. 직접 넣으면 서버가 파트를 못 찾는다.
    fetchMock.mockResolvedValue(jsonResponse(201, []));
    const form = new FormData();
    await request("/x", { method: "POST", body: form });

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.headers).toBeUndefined();
    expect(init.body).toBe(form);
  });

  it("본문이 없으면 헤더도 붙이지 않는다", async () => {
    fetchMock.mockResolvedValue(jsonResponse(201, {}));
    await request("/sessions", { method: "POST" });

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.body).toBeUndefined();
  });
});

describe("실패 처리", () => {
  it("백엔드 에러 코드와 메시지를 그대로 담는다", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(409, { code: "ANOTHER_STEP_IN_PROGRESS", message: "진행 중인 학습 단계가 있습니다." })
    );

    await expect(request("/x", { method: "POST" })).rejects.toMatchObject({
      status: 409,
      code: "ANOTHER_STEP_IN_PROGRESS",
      message: "진행 중인 학습 단계가 있습니다.",
    });
  });

  it("세션이 사라진 경우를 구분할 수 있다", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(404, { code: "SESSION_NOT_FOUND", message: "유효한 학습 세션을 찾을 수 없습니다." })
    );

    const error = await request("/x").catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).isSessionGone).toBe(true);
  });

  it("다른 실패는 세션 문제로 보지 않는다", async () => {
    fetchMock.mockResolvedValue(jsonResponse(404, { code: "TOPIC_NOT_FOUND", message: "없음" }));
    const error = await request("/x").catch((e) => e);
    expect((error as ApiError).isSessionGone).toBe(false);
  });

  it("JSON 이 아닌 오류 응답에도 터지지 않는다", async () => {
    // 프록시가 끼어들면 HTML 오류 페이지가 온다. 파싱하다 터지면 원인이 가려진다.
    fetchMock.mockResolvedValue(textResponse(502, "<html>Bad Gateway</html>"));

    const error = await request("/x").catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(502);
    expect((error as ApiError).code).toBe("UNKNOWN");
  });

  it("서버에 닿지 못하면 NetworkError 로 구분한다", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));
    await expect(request("/x")).rejects.toBeInstanceOf(NetworkError);
  });

  it("요청 취소는 오류로 바꾸지 않는다", async () => {
    // 화면을 벗어나며 취소한 것이라 사용자에게 보여줄 실패가 아니다.
    const abort = new DOMException("aborted", "AbortError");
    fetchMock.mockRejectedValue(abort);
    await expect(request("/x")).rejects.toBe(abort);
  });
});

describe("응답 파싱", () => {
  it("본문이 비어 있으면 null 을 준다", async () => {
    // DELETE 는 204 로 본문 없이 온다.
    fetchMock.mockResolvedValue(textResponse(204, ""));
    await expect(request("/x", { method: "DELETE" })).resolves.toBeNull();
  });

  it("성공 본문을 그대로 돌려준다", async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { sessionCode: "7K2M9QXF" }));
    await expect(request("/x")).resolves.toEqual({ sessionCode: "7K2M9QXF" });
  });
});

describe("toMessage — 화면에 띄울 문구", () => {
  it("백엔드 메시지를 그대로 쓴다", () => {
    expect(toMessage(new ApiError(400, "INVALID_REQUEST", "요청 값이 올바르지 않습니다."))).toBe(
      "요청 값이 올바르지 않습니다."
    );
  });

  it("연결 실패는 안내 문구로 바꾼다", () => {
    expect(toMessage(new NetworkError(new Error("x")))).toContain("서버에 연결할 수 없습니다");
  });

  it("모르는 오류의 내부 메시지를 노출하지 않는다", () => {
    expect(toMessage(new Error("NullPointerException at com.naeil..."))).toBe(
      "알 수 없는 오류가 발생했습니다."
    );
  });
});

/**
 * 실제 백엔드 응답 형태를 못박는다.
 *
 * 연동 작업 중 채점 결과에 문제 본문과 정답이 들어 있다고 <b>추측</b>해서 타입을 썼는데,
 * 실제 응답에는 quizId·selectedIndex·correct·answeredAt 네 개뿐이었다.
 * 타입만 보고 화면을 만들면 런타임에 undefined 가 뜬다.
 *
 * 아래 값은 2026-08-28 실제 Gemini E2E 에서 받은 응답을 그대로 옮긴 것이다.
 */
describe("실제 응답 형태 (E2E 에서 확보한 값)", () => {
  it("채점 결과에는 문제 본문도 정답도 없다", async () => {
    const actual = {
      topicId: "b1fc846b-23aa-495e-ae1f-cbdac5083d01",
      totalQuestions: 5,
      answeredQuestions: 5,
      correctAnswers: 0,
      scorePercentage: 0,
      completed: true,
      results: [
        {
          quizId: "e41c6cbf-3582-406b-a24a-6e15640cddae",
          selectedIndex: 0,
          correct: false,
          answeredAt: "2026-08-28T11:31:20.003438",
        },
      ],
    };
    fetchMock.mockResolvedValue(jsonResponse(200, actual));

    const result = await request<typeof actual>("/x");

    expect(Object.keys(result.results[0]).sort()).toEqual([
      "answeredAt",
      "correct",
      "quizId",
      "selectedIndex",
    ]);
    // 결과 화면은 문제 본문을 퀴즈 조회에서 따로 받아 quizId 로 이어 붙여야 한다.
    expect(result.results[0]).not.toHaveProperty("question");
    expect(result.results[0]).not.toHaveProperty("correctIndex");
  });

  it("퀴즈 조회 응답에 정답이 없다", async () => {
    const actual = {
      topicId: "b1fc846b",
      topicTitle: "스택",
      quizzes: [
        {
          id: "e41c6cbf",
          order: 1,
          question: "스택의 자료 구조 특성에 대한 설명으로 옳은 것은?",
          options: ["a", "b", "c", "d"],
          difficulty: "EASY",
        },
      ],
    };
    fetchMock.mockResolvedValue(jsonResponse(200, actual));

    const result = await request<typeof actual>("/x");

    // 정답을 응답에 실으면 개발자 도구로 다 보인다. 채점은 서버가 한다.
    expect(result.quizzes[0]).not.toHaveProperty("correctIndex");
    expect(result.quizzes[0]).not.toHaveProperty("explanation");
    expect(result.quizzes[0].options).toHaveLength(4);
  });
});
