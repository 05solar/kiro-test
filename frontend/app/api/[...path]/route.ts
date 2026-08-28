import type { NextRequest } from "next/server";

/**
 * `/api/*` 를 백엔드로 넘기는 런타임 프록시.
 *
 * <p><b>next.config 의 rewrites 를 쓰지 않는 이유</b> — rewrites 의 destination 은
 * <b>빌드 시점</b>에 평가되어 라우트 매니페스트에 박힌다. 도커 이미지를 만들 때
 * {@code BACKEND_URL} 이 없으면 {@code localhost:8080} 이 그대로 굳고, 컨테이너에서
 * 실행할 때 환경변수를 줘도 바뀌지 않는다. 실제로 그 상태로 배포해 ECONNREFUSED 를 봤다.
 *
 * <p>여기서는 요청이 올 때마다 환경변수를 읽는다. 같은 이미지를 로컬·스테이징·운영에
 * 그대로 쓰고 주소만 바꿀 수 있다.
 *
 * <p>브라우저는 항상 같은 오리진(`/api/...`)을 부른다. 그래서 CORS 설정이 필요 없고,
 * 백엔드 주소가 브라우저 번들에 들어가지 않는다.
 */

const BACKEND_URL = () => process.env.BACKEND_URL ?? "http://localhost:8080";

/** 프록시가 직접 만들거나 백엔드가 다시 정하는 헤더는 넘기지 않는다. */
const SKIP_REQUEST_HEADERS = new Set(["host", "connection", "content-length"]);
const SKIP_RESPONSE_HEADERS = new Set(["content-encoding", "content-length", "transfer-encoding"]);

async function forward(request: NextRequest, path: string[]): Promise<Response> {
  const search = request.nextUrl.search;
  const target = `${BACKEND_URL()}/api/${path.map(encodeURIComponent).join("/")}${search}`;

  const headers = new Headers();
  request.headers.forEach((value, key) => {
    if (!SKIP_REQUEST_HEADERS.has(key.toLowerCase())) headers.set(key, value);
  });

  // GET/HEAD 에 본문을 실으면 fetch 가 거부한다.
  const hasBody = request.method !== "GET" && request.method !== "HEAD";

  let response: Response;
  try {
    response = await fetch(target, {
      method: request.method,
      headers,
      body: hasBody ? request.body : undefined,
      // 스트리밍 본문(파일 업로드)을 그대로 흘려보내려면 필요하다.
      ...(hasBody ? { duplex: "half" } : {}),
      redirect: "manual",
      // AI 호출은 분 단위로 걸린다. 기본 타임아웃에 걸리지 않게 넉넉히 둔다.
      signal: AbortSignal.timeout(Number(process.env.PROXY_TIMEOUT_MS ?? 300_000)),
    } as RequestInit);
  } catch (error) {
    // 백엔드가 아직 안 떴거나 죽은 경우. 화면이 읽을 수 있는 형태로 돌려준다.
    console.error("[api-proxy] backend unreachable:", target, error);
    return Response.json(
      { code: "BACKEND_UNAVAILABLE", message: "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요." },
      { status: 502 }
    );
  }

  const outHeaders = new Headers();
  response.headers.forEach((value, key) => {
    if (!SKIP_RESPONSE_HEADERS.has(key.toLowerCase())) outHeaders.set(key, value);
  });

  return new Response(response.body, { status: response.status, headers: outHeaders });
}

type Context = { params: Promise<{ path: string[] }> };

// Next 16 에서 params 는 Promise 다. await 없이 쓰면 안 된다.
export async function GET(request: NextRequest, ctx: Context) {
  return forward(request, (await ctx.params).path);
}

export async function POST(request: NextRequest, ctx: Context) {
  return forward(request, (await ctx.params).path);
}

export async function PUT(request: NextRequest, ctx: Context) {
  return forward(request, (await ctx.params).path);
}

export async function DELETE(request: NextRequest, ctx: Context) {
  return forward(request, (await ctx.params).path);
}

export async function PATCH(request: NextRequest, ctx: Context) {
  return forward(request, (await ctx.params).path);
}

/** 프록시 응답을 캐시하지 않는다. 세션마다 다른 값이 온다. */
export const dynamic = "force-dynamic";
