"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { FlowSteps } from "@/app/_components/flow-steps";
// RequiredMark 는 쓰지 않는다. 강의 자료가 선택 입력이 되면서 이 화면에 필수 칸이 없어졌다.
import { AppHeader, CheckMini, Ghost, PrimaryButton, SecondaryButton, SpeechBubble } from "@/app/_components/ui";
import { useSessionStore } from "@/app/_components/session-store";
import { SessionCodeCard } from "@/app/_components/session-code-card";
import { useExamStore } from "@/app/_components/store";
import { useHydrated } from "@/app/_components/use-hydrated";
import {
  deleteDocument,
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

  /** 올린 자료를 지운다. 목록은 서버 기준으로 다시 읽는다. */
  const removeDocument = async (documentId: string) => {
    if (!sessionCode || busy) return;
    setBusy(true);
    setError(null);
    try {
      await deleteDocument(sessionCode, documentId);
      setDocuments(await listDocuments(sessionCode));
    } catch (e) {
      setError(toMessage(e));
    } finally {
      setBusy(false);
    }
  };

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
   *
   * 자료가 하나도 없으면 추출할 것이 없으니 건너뛴다. 그 경우 서버는 과목명과
   * 시험 범위만 보고 일반적인 교과 지식으로 주제를 만든다 — 그래서 시험 범위가 없으면
   * 근거가 아무것도 남지 않는다. 그때는 넘기지 않고 앞 화면으로 돌려보낸다.
   */
  const parseAndContinue = async () => {
    if (!sessionCode) return;

    if (!documents.length && !range.trim()) {
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

      if (documents.length) {
        const parsed = await parseDocuments(sessionCode);
        // 파싱 응답은 목록 조회와 형식이 다르다(documentId, fileSize 없음).
        // 화면 목록은 항상 같은 형식을 쓰도록 다시 조회한다.
        setDocuments(await listDocuments(sessionCode));

        if (!parsed.some((doc) => doc.status === "PARSED")) {
          setError("자료에서 읽을 수 있는 텍스트를 찾지 못했습니다. 다른 파일을 올리거나, 자료 없이 진행해 주세요.");
          return;
        }
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
        {/*
          제목 오른쪽에 세션 코드를 둔다. 세션이 막 발급된 직후 처음 닿는 화면이라
          여기서 보여주지 않으면 사용자가 자기 코드를 볼 기회가 없다.
          좁은 화면에서는 wrap 으로 제목 아래에 내려간다.
        */}
        <div className="mb-9 flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="mb-2.5 text-[13px] font-bold text-[#FF7A00]">STEP 2 / 2</div>
            <h1 className="font-jua mb-2 text-[28px] tracking-[-1px] sm:text-4xl">공부할 자료를 올려주세요</h1>
            <p className="text-[15px] text-[#666]">
              강의 자료를 읽고, 시험에 나올 핵심만 골라 플랜으로 만들게요.
              자료가 없으면 과목명과 시험 범위만으로 만들어 드릴게요.
            </p>
          </div>
          {/* 넓은 화면에서는 위 막대에 이미 있다. 여기서는 좁은 화면에서만 그린다. */}
          <SessionCodeCard className="lg:hidden" />
        </div>

        <div className="grid items-start gap-6 lg:grid-cols-[1fr_300px]">
          <section className="rounded-[18px] border border-[#eee] p-6 sm:p-[34px]">
            <div ref={dropRef} className="mb-[9px] flex items-baseline gap-2">
              <span className="text-[13.5px] font-bold">강의 자료</span>
              <span className="rounded-full bg-[#F4F4F4] px-2 py-0.5 text-[11.5px] font-bold text-[#888]">선택</span>
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
              <div className="mb-5 flex size-16 items-center justify-center rounded-2xl bg-[#FFF3E8]" aria-hidden="true">
                <svg width="30" height="30" viewBox="0 0 24 24" fill="none">
                  <path d="M6 2h8l5 5v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1Z" stroke="#FF7A00" strokeWidth="1.8" strokeLinejoin="round" />
                  <path d="M14 2v5h5" stroke="#FF7A00" strokeWidth="1.8" strokeLinejoin="round" />
                  <path d="M9 13h6M9 17h6" stroke="#FF7A00" strokeWidth="1.8" strokeLinecap="round" />
                </svg>
              </div>
              <strong className="mb-2 text-[17px]">파일을 끌어다 놓거나 클릭해서 선택</strong>
              <span className="text-[13px] leading-6 text-[#666]">PDF, DOCX, TXT · 개당 최대 {MAX_FILE_MB}MB</span>
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
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-[10px] bg-white" aria-hidden="true">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                      <path d="M4 19V5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2Zm0 0a2 2 0 0 0 2 2h13" stroke="#FF7A00" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                      <path d="M9 7h6" stroke="#FF7A00" strokeWidth="1.8" strokeLinecap="round" />
                    </svg>
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-sm font-bold">{doc.originalFileName}</div>
                    <div className="mt-1 text-xs text-[#666]">
                      {formatSize(doc.fileSize)} · {statusLabel(doc.status)}
                      {doc.characterCount ? ` · ${doc.characterCount.toLocaleString()}자` : ""}
                    </div>
                  </div>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => void removeDocument(doc.id)}
                    aria-label={`${doc.originalFileName} 삭제`}
                    className="flex size-8 shrink-0 cursor-pointer items-center justify-center rounded-full border border-transparent text-[#a8886a] transition-colors hover:border-[#F5C2C7] hover:bg-[#FDECEE] hover:text-[#B02A37] disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <path d="M5 5l14 14M19 5 5 19" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" />
                    </svg>
                  </button>
                </div>
              ))}
            </div>

            {!documents.length && !fileMissing && (
              <p className="mt-4 rounded-xl border border-[#D7DDFF] bg-[#F5F7FF] px-4 py-3 text-[13px] leading-[1.7] text-[#3C3F8F]">
                자료가 없어도 괜찮아요. 과목명과 <b>시험 범위</b>만으로 일반적인 교과 지식에 맞춰 플랜을 만듭니다.
                다만 실제 수업 범위와는 일부 차이가 있을 수 있어요.
              </p>
            )}

            {fileMissing && (
              <p role="alert" className="mt-4 text-[13.5px] font-bold text-[#E03131]">
                자료를 올리거나, 앞 화면에서 시험 범위를 적어 주세요. 둘 다 없으면 만들 근거가 없습니다.
              </p>
            )}

            {/*
              학습 맥락 — 강의자료만으로는 알 수 없는 것들.
              전부 선택 입력이다. 비워 둬도 분석과 퀴즈는 그대로 동작한다.
            */}
            <div className="mt-8 border-t border-[#eee] pt-7">
              <div className="mb-1.5 flex items-baseline gap-2">
                <h2 className="text-[15px] font-bold">아는 걸 더 알려주면 더 잘 맞춰요</h2>
                <span className="rounded-full bg-[#F4F4F4] px-2 py-0.5 text-[11.5px] font-bold text-[#666]">선택</span>
              </div>
              <p className="mb-5 text-[13px] leading-[1.7] text-[#666]">
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
                  <span className="mt-2 block text-[12.5px] text-[#666]">
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
                {busy
                  ? "처리하는 중…"
                  : documents.length
                    ? "자료 분석하고 플랜 만들기"
                    : "자료 없이 플랜 만들기"}
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
              <Ghost width={96} className="animate-bob-small" />
            </div>
            <div className="rounded-[18px] border border-[#eee] p-5">
              <h2 className="mb-3 text-[13.5px] font-bold">분석할 내용</h2>
              <ul className="grid gap-2.5 text-[13px] text-[#666]">
                {["단원별 핵심 개념", "시험 출제 가능성", "남은 시간별 학습 순서", "STEP별 확인 퀴즈"].map((item) => (
                  <li key={item} className="flex items-center gap-2">
                    <CheckMini />
                    {item}
                  </li>
                ))}
              </ul>
            </div>
          </aside>
        </div>
      </main>
    </div>
  );
}
