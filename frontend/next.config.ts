import type { NextConfig } from "next";

/**
 * `/api/*` 전달은 여기서 하지 않는다.
 *
 * rewrites 의 destination 은 <b>빌드 시점</b>에 평가되어 라우트 매니페스트에 박힌다.
 * 도커 이미지를 만들 때 백엔드 주소가 없으면 `localhost:8080` 이 그대로 굳고,
 * 컨테이너에서 환경변수를 줘도 바뀌지 않는다.
 *
 * 그래서 `app/api/[...path]/route.ts` 에서 요청마다 환경변수를 읽어 전달한다.
 * 같은 이미지를 어느 환경에나 올리고 `BACKEND_URL` 만 바꾸면 된다.
 */
const nextConfig: NextConfig = {
  /**
   * 도커 이미지를 작게 만들기 위해 실행에 필요한 파일만 모아 낸다.
   *
   * 이걸 켜지 않으면 이미지에 node_modules 전체(수백 MB)를 넣어야 한다.
   * standalone 은 실제로 쓰이는 의존성만 추려 `.next/standalone` 에 담는다.
   */
  output: "standalone",

};

export default nextConfig;
