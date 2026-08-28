"use client";

import { useRouter } from "next/navigation";
import { AppHeader, Ghost, PrimaryButton } from "@/app/_components/ui";

const FEATURES = [
  ["01", "남은 시간에 맞춘 분량", "6시간이면 6시간짜리 플랜. 못 볼 분량은 애초에 넣지 않습니다."],
  ["02", "한 칸씩 나아가는 맵", "STEP을 길 위에 놓고, 깰 때마다 앞으로. 상자를 열면 바나나를 받습니다."],
  ["03", "퀴즈로 확인", "STEP마다 4지선다. 틀린 개념은 취약 개념으로 모아 다시 보여줍니다."],
] as const;

export default function HomePage() {
  const router = useRouter();

  return (
    <div className="min-h-screen bg-white text-[#222]">
      <AppHeader />
      <main>
        <section className="mx-auto grid max-w-[1240px] items-center gap-10 px-6 py-16 md:grid-cols-[1.15fr_.85fr] md:px-10 md:pb-16 md:pt-[88px]">
          <div>
            <div className="mb-[26px] inline-block rounded-full bg-[#FFF3E8] px-3 py-1.5 text-[12.5px] font-bold text-[#E85D00]">
              시험 D-1 전용 학습 도우미
            </div>
            <h1 className="font-jua m-0 mb-[22px] text-[48px] leading-[1.14] tracking-[-2px] text-[#222] sm:text-[66px]">
              내일까지<br />해야 하는데<span className="text-[#FF7A00]">.</span>
            </h1>
            <p className="mb-[38px] max-w-[470px] text-[17px] leading-[1.65] text-[#888] sm:text-[19px]">
              남은 시간과 시험 범위를 넣으면, 지금부터 시험 시작까지 뭘 어떤 순서로 볼지 STEP으로 잘라서 알려줍니다.
            </p>
            <div className="flex flex-wrap items-center gap-4">
              <PrimaryButton className="px-[34px] py-[17px] text-[17px]" onClick={() => router.push("/exam-info")}>
                벼락치기 시작하기
              </PrimaryButton>
              <span className="text-[13.5px] text-[#888]">1분이면 플랜이 나옵니다</span>
            </div>
          </div>
          <div className="relative flex h-[320px] items-center justify-center md:h-[360px]">
            <div className="absolute size-[260px] rounded-full bg-[#FFF3E8] sm:size-[300px]" />
            <div className="font-jua absolute right-2 top-[22px] rounded-[14px_14px_14px_4px] border border-[#eee] bg-white px-[15px] py-2.5 text-[15px] text-[#222] shadow-[0_4px_14px_rgba(0,0,0,.06)]">
              아직 안 늦었어!
            </div>
            <Ghost width={190} mood="plain" className="animate-bob relative drop-shadow-[0_14px_22px_rgba(255,122,0,.25)]" />
          </div>
        </section>
        <section className="mx-auto grid max-w-[1240px] gap-5 px-6 pb-[100px] md:grid-cols-3 md:px-10">
          {FEATURES.map(([number, title, description]) => (
            <article key={number} className="rounded-2xl border border-[#eee] bg-white px-7 py-[30px]">
              <div className="font-jua mb-3.5 text-[15px] text-[#FF7A00]">{number}</div>
              <h2 className="mb-[9px] text-[17.5px] font-bold">{title}</h2>
              <p className="text-sm leading-[1.7] text-[#888]">{description}</p>
            </article>
          ))}
        </section>
      </main>
    </div>
  );
}
