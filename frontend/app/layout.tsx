import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "내일까지 | 시험 D-1 학습 도우미",
    template: "%s | 내일까지",
  },
  description: "남은 시간과 시험 범위에 맞춰 가장 중요한 학습 순서를 STEP으로 만들어 주는 벼락치기 학습 도우미입니다.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className="h-full">
      <body className="min-h-full">{children}</body>
    </html>
  );
}
