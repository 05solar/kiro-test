/**
 * 벼락치기 맵의 모든 칸을 나타내는 단일 소스.
 * 경로(TRACK_PATH_D)는 원본 디자인의 하드코딩 곡선을 그대로 사용한다.
 * (Catmull-Rom 자동 생성 경로는 곡선 품질 저하로 폐기됨)
 *
 * 배열 index = 진행 순서(step-1 → reward-1 → step-2 → ... → step-7 → goal).
 * step/goal/reward 좌표는 모두 원본 NODE_CENTERS/TREASURES/GOAL 값이며
 * TRACK_PATH_D 위에 정확히 얹히도록 디자인된 값이다(경로 위 마커 배치가 아님).
 *
 * 좌표는 SVG viewBox "0 0 900 600" 기준.
 */
export type MapNode =
  | { id: string; kind: "step"; stepId: number; x: number; y: number; label: string }
  | { id: string; kind: "goal"; x: number; y: number; label: string }
  | { id: string; kind: "reward"; afterStepId: number; x: number; y: number; label: string };

export type GeoNode = Extract<MapNode, { kind: "step" | "goal" }>;
export type RewardNode = Extract<MapNode, { kind: "reward" }>;

/** 원본 디자인의 하드코딩 경로. 배경 트랙과 완주 경로 모두 이 값만 사용한다. */
export const TRACK_PATH_D =
  "M70 500 C130 492 152 476 182 466 C232 450 244 430 286 420 C346 406 362 440 414 446 C476 453 494 396 524 358 C550 325 566 306 580 280 C600 244 512 254 456 250 C392 245 372 210 322 186 C288 169 342 136 432 134 C512 132 556 156 602 156 C682 156 726 122 792 92";

export const MAP_NODES: readonly MapNode[] = [
  { id: "step-1", kind: "step", stepId: 1, x: 70, y: 500, label: "스택 기초" },
  { id: "reward-1", kind: "reward", afterStepId: 1, x: 182, y: 466, label: "바나나 상자" },
  { id: "step-2", kind: "step", stepId: 2, x: 286, y: 420, label: "스택 응용" },
  { id: "step-3", kind: "step", stepId: 3, x: 414, y: 446, label: "큐와 원형 큐" },
  // STEP 4 라벨("덱 · 우선순위 큐")과 겹치지 않도록 좌측으로 이동(요청에 따른 예외 조정).
  { id: "reward-2", kind: "reward", afterStepId: 3, x: 484, y: 358, label: "바나나 상자" },
  { id: "step-4", kind: "step", stepId: 4, x: 580, y: 280, label: "덱 · 우선순위 큐" },
  { id: "step-5", kind: "step", stepId: 5, x: 456, y: 250, label: "트리 기초" },
  { id: "reward-3", kind: "reward", afterStepId: 5, x: 432, y: 134, label: "바나나 상자" },
  { id: "step-6", kind: "step", stepId: 6, x: 322, y: 186, label: "이진 탐색 트리" },
  { id: "step-7", kind: "step", stepId: 7, x: 602, y: 156, label: "그래프 순회" },
  { id: "goal", kind: "goal", x: 792, y: 92, label: "시험장" },
];

/**
 * 각 STEP 노드가 TRACK_PATH_D 위에서 차지하는 위치 비율(0~1).
 * getPointAtLength로 경로를 촘촘히 샘플링해 각 노드 좌표와 가장 가까운 지점을 찾아 계산한 값을
 * 상수로 고정했다(런타임에 재계산하지 않음). 노드 좌표가 바뀌면 이 표도 다시 계산해야 한다.
 */
export const NODE_PROGRESS: Record<number, number> = {
  1: 0,
  2: 0.162,
  3: 0.2551,
  4: 0.4274,
  5: 0.5283,
  6: 0.6336,
  7: 0.8582,
};

export const GOAL_PROGRESS = 1;

/**
 * STEP 라벨의 노드 중심 기준 offset(px). 기본값은 노드 바로 아래.
 * STEP별로 라벨이 경로 곡선이나 상자 아이콘과 겹치는 경우에만 개별 조정한다.
 */
/**
 * offset은 노드 크기(size)와 무관하게 node.x/node.y 좌표 기준 절대값이다.
 * (isCurrent 상태에서 노드 지름이 88px까지 커지므로, "노드 테두리 기준" 상대 오프셋을 쓰면
 *  숫자가 라벨에 가려지는 회귀가 재발한다. 항상 노드 중심에서 56px 이상 떨어뜨린다.)
 */
export const NODE_LABEL_OFFSET: Record<number, { dx: number; dy: number }> = {
  4: { dx: 24, dy: 56 }, // 좌측의 바나나 상자(reward-2)와 더 떨어지도록 우측으로 이동
  7: { dx: -90, dy: 56 }, // 경로 곡선과 최소 20px 이상 떨어지도록 좌하단으로 이동
};

export const DEFAULT_NODE_LABEL_OFFSET = { dx: 0, dy: 56 };

/**
 * 상자를 놓을 위치 규칙 — "몇 번째 STEP 뒤에 상자가 있는가".
 *
 * 실제 STEP 수는 계획마다 다르므로 좌표가 아니라 규칙으로 정한다.
 * 첫 STEP 부터 한 칸 건너 하나씩(1·3·5…번째 뒤), 마지막 STEP 뒤에는 두지 않는다
 * (거기는 시험장 도착이 보상이다).
 *
 * @param stepIds 살아있는 STEP id 를 진행 순서대로
 * @return 상자가 붙는 STEP id 목록 (그 STEP 을 완료하면 상자가 열린다)
 */
export function rewardAfterSteps(stepIds: readonly number[]): number[] {
  return stepIds.filter((_, index) => index % 2 === 0 && index < stepIds.length - 1);
}
