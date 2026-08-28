"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { FlowSteps } from "@/app/_components/flow-steps";
import { AppHeader, Ghost, PrimaryButton, RequiredMark, SecondaryButton, SpeechBubble } from "@/app/_components/ui";
import { useSessionStore } from "@/app/_components/session-store";
import { useExamStore } from "@/app/_components/store";
import { useHydrated } from "@/app/_components/use-hydrated";
import {
  listDocuments,
  parseDocuments,
  toMessage,
  updateStudyContext,
  uploadDocuments,
  type DocumentResponse,
} from "@/lib/api";

/** 백엔드가 받는 형식. 안내 문구와 input accept 를 서버 제한과 같은 값으로 맞춘다. */
const ACCEPT = ".pdf,.docx,.txt";
const MAX_FILE_MB = 20;

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)}KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
}

function statusLabel(status: DocumentResponse["status"]): string {
  switch (status) {
    case "PARSED":
      return "읽기 완료";
    case "PARSING":
      return "읽는 중";
    case "PARSE_FAILED":
      return "읽기 실패";
    default:
      return "업로드됨";
  }
}

export default function UploadPage() {
  const router = useRouter();
  const hydrated = useHydrated();
  const inputRef = useRef<HTMLInputElement>(null);
  const sessionCode = useSessionStore((state) => state.sessionCode);

  const [documents, setDocuments] = useState<DocumentResponse[]>([]);
  const [dragging, setDragging] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fileMissing, setFileMissing] = useState(false);

  const dropRef = useRef<HTMLDivElement>(null);

  // 시험 정보에서 받은 시험 범위. 학습 맥락의 "반드시 공부할 범위"로 함께 보낸다.
  const range = useExamStore((state) => state.range);

  /*
   * 학습 맥락 — 전부 선택 입력이다.
   *
   * 강의자료만으로는 알 수 없는 것들이다. 교수님이 무엇을 강조했는지, 작년에 뭐가 나왔는지,
   * 본인이 어디가 약한지. 채우면 분석과 출제 방향이 그쪽으로 기운다.
   * 비워 둬도 나머지 기능은 그대로 동작한다.
   */
  const [professorEmphasis, setProfessorEmphasis] = useState("");
  const [pastExamInfo, setPastExamInfo] = useState("");
  const [weakAreas, setWeakAreas] = useState("");
  const [mustStudyAreas, setMustStudyAreas] = useState("");

  // 세션 없이 들어온 경우는 처음부터 시작해야 한다.
  useEffect(() => {
    if (hydrated && !sessionCode) router.replace("/exam-info");
  }, [hydrated, sessionCode, router]);

  // 새로고침해도 이미 올린 자료가 보여야 한다. 목록은 서버가 갖고 있다.
  useEffect(() => {
    if (!sessionCode) return;
    let active = true;
    void listDocuments(sessionCode)
      .then((list) => {
        if (active) setDocuments(list);
      })
      .catch(() => {
        // 목록 조회 실패는 화면을 막을 이유가 아니다. 업로드하면 다시 채워진다.
      });
    return () => {
      active = false;
    };
  }, [sessionCode]);

  const selectFiles = async (fileList: FileList | null) => {
    const files = Array.from(fileList ?? []);
    if (!files.length || !sessionCode) return;

    const tooLarge = files.find((file) => file.size > MAX_FILE_MB * 1024 * 1024);
    if (tooLarge) {
      // 서버도 막지만, 20MB 를 올려보내고 실패를 기다리게 하지 않는다.
      setError(`${tooLarge.name}은(는) ${MAX_FILE_MB}MB를 넘습니다.`);
      return;
    }

    setBusy(true);
    setError(null);
    try {
      await uploadDocuments(sessionCode, files);
      setDocuments(await listDocuments(sessionCode));
      setFileMissing(false);
    } catch (e) {
      setError(toMessage(e));
    } finally {
      setBusy(false);
    }
  };

  /**
   * 텍스트를 추출하고 분석 화면으로 넘어간다.
   *
   * 업로드와 추출을 나눠 둔 것은 서버 설계 그대로다. 20MB 파일 여러 개를 받으면서
   * 동시에 파싱까지 하면 업로드 응답이 한참 돌아오지 않는다.
   */
  const parseAndContinue = async () => {
    if (!sessionCode) return;

    // 자료가 없으면 분석할 것이 없다. 업로드 영역으로 데려가 표시한다.
    if (!documents.length) {
      setFileMissing(true);
      dropRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
      return;
    }

    setBusy(true);
    setError(null);
    try {
      /*
       * 학습 맥락을 함께 남긴다. 전부 선택 입력이라 비어 있으면 null 로 보낸다.
       * PUT 이라 부분 수정이 아니라 전체 교체다 — 안 보낸 항목은 서버에서도 비워진다.
       *
       * "반드시 공부할 범위"는 사용자가 직접 적은 값을 우선하고,
       * 비어 있으면 시험 정보에서 받은 시험 범위를 쓴다.
       */
      const mustStudy = mustStudyAreas.trim() || range.trim();
      await updateStudyContext(sessionCode, {
        professorEmphasis: professorEmphasis.trim() || null,
        pastExamInfo: pastExamInfo.trim() || null,
        weakAreas: weakAreas.trim() || null,
        mustStudyAreas: mustStudy || null,
      });

      const parsed = await parseDocuments(sessionCode);
      setDocuments(parsed);

      if (!parsed.some((doc) => doc.status === "PARSED")) {
        setError("자료에서 읽을 수 있는 텍스트를 찾지 못했습니다. 다른 파일을 올려 주세요.");
        return;
      }
      router.push("/analysis");
    } catch (e) {
      setError(toMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen bg-white text-[#222]">
      <AppHeader />
      <main className="mx-auto max-w-[980px] px-6 py-12 md:px-10 md:pb-24 md:pt-14">
        <FlowSteps current={1} />
        <div className="mb-2.5 text-[13px] font-bold text-[#FF7A00]">STEP 2 / 2</div>
        <h1 className="font-jua mb-2 text-4xl tracking-[-1px]">공부할 자료를 올려주세요</h1>
        <p className="mb-9 text-[15px] text-[#888]">
          강의 자료를 읽고, 시험에 나올 핵심만 골라 플랜으로 만들게요.{" "}
          <span className="font-bold text-[#E03131]">*</span> 는 필수 입력이에요.
        </p>

        <div className="grid items-start gap-6 lg:grid-cols-[1fr_300px]">
          <section className="rounded-[18px] border border-[#eee] p-6 sm:p-[34px]">
            <div ref={dropRef} className="mb-[9px] text-[13.5px] font-bold">
              강의 자료<RequiredMark />
            </div>
            <button
              type="button"
              onClick={() => inputRef.current?.click()}
              onDragEnter={(event) => { event.preventDefault(); setDragging(true); }}
              onDragOver={(event) => event.preventDefault()}
              onDragLeave={() => setDragging(false)}
              onDrop={(event) => { event.preventDefault(); setDragging(false); void selectFiles(event.dataTransfer.files); }}
              aria-invalid={fileMissing}
              className={`flex min-h-[250px] w-full cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed px-6 text-center transition-colors ${
                dragging
                  ? "border-[#FF7A00] bg-[#FFF3E8]"
                  : fileMissing
                    ? "border-[#E03131] bg-[#FDECEE]"
                    : "border-[#FFE0C4] bg-[#FFFDFB] hover:bg-[#FFF3E8]"
              }`}
            >
              <div className="mb-5 flex size-16 items-center justify-center rounded-2xl bg-[#FFF3E8] text-[30px]" aria-hidden="true">📄</div>
              <strong className="mb-2 text-[17px]">파일을 끌어다 놓거나 클릭해서 선택</strong>
              <span className="text-[13px] leading-6 text-[#888]">PDF, DOCX, TXT · 개당 최대 {MAX_FILE_MB}MB</span>
            </button>
            <input
              ref={inputRef}
              type="file"
              multiple
              accept={ACCEPT}
              className="hidden"
              onChange={(event) => void selectFiles(event.target.files)}
            />

            <div className="mt-4 grid gap-2.5">
              {documents.map((doc) => (
                <div key={doc.id} className="flex items-center gap-3 rounded-xl border border-[#FFE0C4] bg-[#FFF3E8] px-4 py-3.5">
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-[10px] bg-white text-xl">📚</span>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-sm font-bold">{doc.originalFileName}</div>
                    <div className="mt-1 text-xs text-[#888]">
                      {formatSize(doc.fileSize)} · {statusLabel(doc.status)}
                      {doc.characterCount ? ` · ${doc.characterCount.toLocaleString()}자` : ""}
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {fileMissing && (
              <p role="alert" className="mt-4 text-[13.5px] font-bold text-[#E03131]">
                공부할 자료를 한 개 이상 올려 주세요.
              </p>
            )}

            {/*
              학습 맥락 — 강의자료만으로는 알 수 없는 것들.
              전부 선택 입력이다. 비워 둬도 분석과 퀴즈는 그대로 동작한다.
            */}
            <div className="mt-8 border-t border-[#eee] pt-7">
              <div className="mb-1.5 flex items-baseline gap-2">
                <h2 className="text-[15px] font-bold">아는 걸 더 알려주면 더 잘 맞춰요</h2>
                <span className="rounded-full bg-[#F4F4F4] px-2 py-0.5 text-[11.5px] font-bold text-[#888]">선택</span>
              </div>
              <p className="mb-5 text-[13px] leading-[1.7] text-[#888]">
                자료에는 없는 정보예요. 채우면 그쪽 내용을 먼저 배치하고 문제도 그 방향으로 냅니다.
                비워 두셔도 됩니다.
              </p>

              <div className="grid gap-5">
                <label className="block">
                  <span className="mb-[9px] block text-[13.5px] font-bold">교수님이 강조한 내용</span>
                  <textarea
                    value={professorEmphasis}
                    onChange={(event) => setProfessorEmphasis(event.target.value)}
                    placeholder="ex ) 스케줄링 알고리즘은 꼭 나온다고 하셨어요"
                    rows={2}
                    maxLength={2000}
                    className="form-input resize-y"
                  />
                </label>
                <label className="block">
                  <span className="mb-[9px] block text-[13.5px] font-bold">기출 · 예상 문제</span>
                  <textarea
                    value={pastExamInfo}
                    onChange={(event) => setPastExamInfo(event.target.value)}
                    placeholder="ex ) 작년에 교착상태 네 가지 조건을 쓰는 문제가 나왔어요"
                    rows={2}
                    maxLength={2000}
                    className="form-input resize-y"
                  />
                </label>
                <label className="block">
                  <span className="mb-[9px] block text-[13.5px] font-bold">자신 없는 부분</span>
                  <textarea
                    value={weakAreas}
                    onChange={(event) => setWeakAreas(event.target.value)}
                    placeholder="ex ) 가상 메모리 페이지 교체가 계속 헷갈려요"
                    rows={2}
                    maxLength={2000}
                    className="form-input resize-y"
                  />
                </label>
                <label className="block">
                  <span className="mb-[9px] block text-[13.5px] font-bold">반드시 봐야 하는 범위</span>
                  <textarea
                    value={mustStudyAreas}
                    onChange={(event) => setMustStudyAreas(event.target.value)}
                    placeholder={range ? `ex ) ${range}` : "ex ) 3장 스택 ~ 5장 트리"}
                    rows={2}
                    maxLength={2000}
                    className="form-input resize-y"
                  />
                  <span className="mt-2 block text-[12.5px] text-[#888]">
                    여기 적은 범위는 시간이 모자라도 플랜에서 빼지 않아요.
                    {range && !mustStudyAreas.trim() ? ` 비워 두면 시험 범위(${range})를 씁니다.` : ""}
                  </span>
                </label>
              </div>
            </div>

            {error && (
              <p role="alert" className="mt-4 rounded-xl border border-[#F5C2C7] bg-[#FDECEE] px-4 py-3 text-[13.5px] text-[#B02A37]">
                {error}
              </p>
            )}

            <div className="mt-7 flex flex-wrap gap-3">
              <PrimaryButton className="flex-1" disabled={busy} onClick={() => void parseAndContinue()}>
                {busy ? "처리하는 중…" : "자료 분석하고 플랜 만들기"}
              </PrimaryButton>
              <SecondaryButton onClick={() => router.push("/exam-info")}>이전</SecondaryButton>
            </div>
          </section>

          <aside className="grid gap-4">
            <div className="grid justify-items-center gap-3 rounded-[18px] border border-[#eee] px-5 py-6">
              <SpeechBubble tail="bottom-left" className="text-center">
                목차가 있는 자료면 더 정확해!<br />
                <span className="text-[#E85D00]">여러 개를 한 번에</span> 올려도 돼.
              </SpeechBubble>
              <Ghost width={72} mood="plain" className="animate-bob-small" />
            </div>
            <div className="rounded-[18px] border border-[#eee] p-5">
              <h2 className="mb-3 text-[13.5px] font-bold">분석할 내용</h2>
              <ul className="grid gap-2.5 text-[13px] text-[#888]">
                <li>✓ 단원별 핵심 개념</li>
                <li>✓ 시험 출제 가능성</li>
                <li>✓ 남은 시간별 학습 순서</li>
                <li>✓ STEP별 확인 퀴즈</li>
              </ul>
            </div>
          </aside>
        </div>
      </main>
    </div>
  );
}
