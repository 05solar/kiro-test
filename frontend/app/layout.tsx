import type { Metadata } from "next";
import "./globals.css";

/**
 * 링크 미리보기에 쓸 절대 주소.
 *
 * <p>og:image 는 상대 경로를 허용하지 않는다 — 카카오톡·슬랙 크롤러는 우리 페이지를
 * 열어 둔 상태가 아니라 메타 태그만 긁어 가므로, 이미지 주소가 완전한 형태여야 한다.
 * 배포 주소는 환경마다 다르니 빌드 시점에 주입하고, 없으면 로컬 주소로 둔다.
 */
const siteUrl = process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000";

const title = "내일까지 | 시험 D-1 학습 도우미";
const description =
  "남은 시간과 시험 범위에 맞춰 가장 중요한 학습 순서를 STEP으로 만들어 주는 벼락치기 학습 도우미입니다.";

/*
 * 파비콘과 미리보기 이미지는 파일 이름 규칙으로 붙는다 — 여기에 따로 적지 않는다.
 *   app/icon.png            → <link rel="icon">        (탭 아이콘)
 *   app/apple-icon.png      → <link rel="apple-touch-icon">
 *   app/opengraph-image.png → og:image / twitter:image
 * 셋 다 메인 캐릭터를 그대로 쓴다. 로고와 탭 아이콘이 다르면 같은 서비스로 보이지 않는다.
 */
export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: title,
    template: "%s | 내일까지",
  },
  description,
  applicationName: "내일까지 해야 하는데",
  openGraph: {
    type: "website",
    locale: "ko_KR",
    url: siteUrl,
    siteName: "내일까지 해야 하는데",
    title,
    description,
  },
  twitter: {
    card: "summary_large_image",
    title,
    description,
  },
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className="h-full">
      <body className="min-h-full">{children}</body>
    </html>
  );
}
