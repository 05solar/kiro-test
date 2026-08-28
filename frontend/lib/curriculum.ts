/**
 * 커리큘럼 생성 순수 함수 모듈.
 * 컴포넌트에서 로직을 만들지 않고 이 모듈만 소비한다.
 * (다음 라운드에서 맵 노드가 CurriculumResult를 그대로 재사용할 예정)
 */

export type PrepState = "none" | "skimmed" | "review";
export type Importance = "high" | "mid" | "low";
export type StepMode = "full" | "skim" | "review";

export interface BaseStep {
  id: number;
  title: string;
  importance: Importance;
  baseMinutes: number;
}

export interface CurriculumStep {
  id: number;
  title: string;
  importance: Importance;
  minutes: number;
  baseMinutes: number;
  mode: StepMode;
  quizFirst: boolean;
}

export interface CurriculumResult {
  steps: CurriculumStep[];
  totalMinutes: number;
  baseTotalMinutes: number;
  reductionPct: number;
  cutStepIds: number[];
}

const IMPORTANCE_ORDER: Record<Importance, number> = { high: 0, mid: 1, low: 2 };
const CUT_PRIORITY: Record<Importance, number> = { low: 0, mid: 1, high: 2 };

/**
 * @param baseSteps       기본 스텝 정의
 * @param prepState       사용자의 현재 준비 상태
 * @param availableMinutes 플랜 생성 시점에 확정된 공부 가능 시간(분). Infinity면 컷 없음.
 * @param currentStep     진행 중/완료 기준(1-indexed). 이 값 이하 id는 컷 대상에서 제외.
 */
export function generateCurriculum(
  baseSteps: readonly BaseStep[],
  prepState: PrepState,
  availableMinutes: number,
  currentStep = 0
): CurriculumResult {
  // 1) prepState에 따라 각 스텝의 시간/모드/퀴즈우선 결정
  let steps: CurriculumStep[] = baseSteps.map((step) => {
    let mode: StepMode = "full";
    let quizFirst = false;
    let minutes = step.baseMinutes;

    if (prepState === "review") {
      // 개념 최소화, 스텝마다 퀴즈 우선
      mode = "review";
      quizFirst = true;
      minutes = Math.round(step.baseMinutes * 0.4);
    } else if (prepState === "skimmed") {
      if (step.importance === "low") {
        // 중요도 낮은 스텝은 '빠르게 훑기'로 축약
        mode = "skim";
        minutes = Math.round(step.baseMinutes * 0.4);
      } else {
        mode = "full";
        minutes = Math.round(step.baseMinutes * 0.65);
      }
    } else {
      // none: 전체 기본 시간, 개념 학습 중심
      mode = "full";
      minutes = step.baseMinutes;
    }

    return {
      id: step.id,
      title: step.title,
      importance: step.importance,
      minutes,
      baseMinutes: step.baseMinutes,
      mode,
      quizFirst,
    };
  });

  // 2) skimmed는 핵심(high) 스텝을 앞으로 재배치(동일 중요도는 원래 순서 유지)
  if (prepState === "skimmed") {
    steps = steps
      .map((step, index) => ({ step, index }))
      .sort(
        (a, b) =>
          IMPORTANCE_ORDER[a.step.importance] - IMPORTANCE_ORDER[b.step.importance] ||
          a.index - b.index
      )
      .map((entry) => entry.step);
  }

  const baseTotalMinutes = baseSteps.reduce((total, step) => total + step.baseMinutes, 0);

  // 3) 총 소요시간이 availableMinutes를 넘으면 low importance부터 컷.
  //    단, currentStep 이하(완료/진행 중)는 컷하지 않는다.
  const cutStepIds: number[] = [];
  const sumMinutes = () => steps.reduce((total, step) => total + step.minutes, 0);

  if (Number.isFinite(availableMinutes)) {
    while (sumMinutes() > availableMinutes) {
      let victimIndex = -1;
      for (let i = 0; i < steps.length; i += 1) {
        const candidate = steps[i];
        if (candidate.id <= currentStep) continue; // 보호 대상
        if (victimIndex === -1) {
          victimIndex = i;
          continue;
        }
        const current = steps[victimIndex];
        const candidateRank = CUT_PRIORITY[candidate.importance];
        const currentRank = CUT_PRIORITY[current.importance];
        // 더 낮은 중요도 우선, 같으면 뒤(나중 진행)에 있는 스텝을 먼저 컷
        if (candidateRank < currentRank || (candidateRank === currentRank && i > victimIndex)) {
          victimIndex = i;
        }
      }
      if (victimIndex === -1) break; // 더 이상 자를 수 있는 스텝이 없음
      cutStepIds.push(steps[victimIndex].id);
      steps.splice(victimIndex, 1);
    }
  }

  const totalMinutes = sumMinutes();
  const reductionPct =
    baseTotalMinutes > 0 ? Math.round((1 - totalMinutes / baseTotalMinutes) * 100) : 0;

  return { steps, totalMinutes, baseTotalMinutes, reductionPct, cutStepIds };
}

/** 분 단위를 'N시간 M분' 형태 문자열로. 음수/NaN은 0분으로 방어. */
export function formatMinutes(minutes: number): string {
  const safe = Number.isFinite(minutes) ? Math.max(0, Math.round(minutes)) : 0;
  const hours = Math.floor(safe / 60);
  const rest = safe % 60;
  if (hours && rest) return `${hours}시간 ${rest}분`;
  if (hours) return `${hours}시간`;
  return `${rest}분`;
}
