export const OPTION_TAGS = ["A", "B", "C", "D"] as const;

export const BASE_STEPS = [
  { id: 1, title: "스택 기초", importance: "mid", baseMinutes: 40 },
  { id: 2, title: "스택 응용 · 후위 표기식", importance: "high", baseMinutes: 55 },
  { id: 3, title: "큐와 원형 큐", importance: "high", baseMinutes: 50 },
  { id: 4, title: "덱 · 우선순위 큐", importance: "mid", baseMinutes: 45 },
  { id: 5, title: "트리 기초", importance: "high", baseMinutes: 55 },
  { id: 6, title: "이진 탐색 트리", importance: "mid", baseMinutes: 50 },
  { id: 7, title: "그래프 순회", importance: "low", baseMinutes: 45 },
] as const;

export const STEPS = BASE_STEPS.map((step) => step.title);

export type StudyContent = {
  id: number;
  title: string;
  chapter: string;
  importanceLabel: string;
  importanceNote: string;
  summary: string;
  concepts: readonly { title: string; description: string }[];
  examTip: string;
  characterComment: string;
};

/** URL stepId로 find해서 사용하는 STEP별 학습 콘텐츠. */
export const STUDY_CONTENT: readonly StudyContent[] = [
  {
    id: 1,
    title: "스택 기초",
    chapter: "3장 스택",
    importanceLabel: "보통",
    importanceNote: "기본 정의·연산 출제",
    summary: "스택은 한쪽 끝 top에서만 삽입과 삭제가 일어나는 LIFO 자료구조입니다.",
    concepts: [
      { title: "LIFO — 나중에 들어온 것이 먼저 나간다", description: "접시를 쌓듯 가장 최근에 넣은 원소가 가장 먼저 나옵니다." },
      { title: "push / pop", description: "push는 top에 삽입하고, pop은 top 원소를 제거하면서 반환합니다." },
      { title: "스택 포인터 top", description: "빈 스택은 top=-1, 배열 스택의 포화 조건은 top=N-1입니다." },
    ],
    examTip: "연산 순서를 제시하고 마지막 top 값이나 pop 결과를 묻는 문제가 자주 나옵니다.",
    characterComment: "기초부터 단단히! LIFO만 기억하면 반은 끝이야.",
  },
  {
    id: 2,
    title: "스택 응용 · 후위 표기식",
    chapter: "3장 스택 응용",
    importanceLabel: "높음",
    importanceNote: "후위 표기식 단골 출제",
    summary: "연산자를 스택에 보관해 중위 표기식을 후위 표기식으로 변환하고 계산합니다.",
    concepts: [
      { title: "중위 → 후위 변환", description: "피연산자는 바로 출력하고 연산자는 우선순위에 따라 스택에 넣습니다." },
      { title: "연산자 우선순위", description: "괄호와 곱셈·나눗셈, 덧셈·뺄셈의 우선순위를 스택에서 비교합니다." },
      { title: "후위 표기식 계산", description: "피연산자를 쌓고 연산자를 만나면 두 값을 꺼내 계산한 뒤 다시 넣습니다." },
    ],
    examTip: "A+B*C 같은 식을 직접 후위 표기식으로 바꾸는 과정을 한 번 손으로 따라가세요.",
    characterComment: "연산자만 스택에! 순서를 천천히 따라가면 돼.",
  },
  {
    id: 3,
    title: "큐와 원형 큐",
    chapter: "4장 큐",
    importanceLabel: "높음",
    importanceNote: "기출 3회 출제",
    summary: "큐는 FIFO로 동작하며, 원형 큐는 나머지 연산으로 배열 공간을 재사용합니다.",
    concepts: [
      { title: "FIFO — 먼저 들어온 것이 먼저 나간다", description: "삽입은 rear에서, 삭제는 front에서 이루어집니다. 스택의 LIFO와 반대입니다." },
      { title: "enqueue / dequeue", description: "선형 배열 큐는 dequeue 후 앞 공간을 재사용하지 못해 공간이 낭비됩니다." },
      { title: "원형 큐의 인덱스 계산", description: "rear=(rear+1)%N, front=(front+1)%N. 포화 조건은 (rear+1)%N==front입니다." },
      { title: "연결 리스트 큐", description: "rear 포인터를 유지하면 enqueue와 dequeue를 모두 O(1)에 수행합니다." },
    ],
    examTip: "크기 N인 원형 큐의 삽입·삭제 표를 보고 front와 rear 값을 묻는 문제를 대비하세요.",
    characterComment: "여긴 중요해! front와 rear를 손으로 그려보자.",
  },
  {
    id: 4,
    title: "덱 · 우선순위 큐",
    chapter: "4장 큐 응용",
    importanceLabel: "보통",
    importanceNote: "자료구조 비교 출제",
    summary: "덱은 양쪽에서 삽입·삭제하고, 우선순위 큐는 우선순위가 높은 원소를 먼저 꺼냅니다.",
    concepts: [
      { title: "덱(Deque)", description: "front와 rear 양쪽 모두에서 삽입과 삭제가 가능한 큐입니다." },
      { title: "입력 제한·출력 제한 덱", description: "한쪽 연산을 제한하면 스택이나 큐의 동작을 표현할 수 있습니다." },
      { title: "우선순위 큐", description: "도착 순서보다 우선순위가 높은 원소가 먼저 삭제되며 보통 힙으로 구현합니다." },
    ],
    examTip: "일반 큐·덱·우선순위 큐의 삭제 기준을 비교하는 선택지를 주의하세요.",
    characterComment: "양쪽 문이 있는 큐라고 생각하면 덱이 쉬워져.",
  },
  {
    id: 5,
    title: "트리 기초",
    chapter: "6장 트리",
    importanceLabel: "높음",
    importanceNote: "용어·순회 필수",
    summary: "트리는 계층 관계를 표현하며 루트, 부모·자식, 차수, 높이 개념이 핵심입니다.",
    concepts: [
      { title: "노드와 간선", description: "노드는 데이터를, 간선은 노드 사이의 계층 관계를 나타냅니다." },
      { title: "차수와 높이", description: "노드의 차수는 자식 수이며 트리 높이는 루트에서 가장 깊은 리프까지의 거리입니다." },
      { title: "이진 트리", description: "모든 노드가 최대 두 자식을 가지며 완전·포화 이진 트리를 구분해야 합니다." },
    ],
    examTip: "주어진 트리 그림에서 리프 수, 특정 노드의 차수, 트리 높이를 빠르게 세어보세요.",
    characterComment: "뿌리에서 잎까지! 용어를 그림과 연결하면 빨라.",
  },
  {
    id: 6,
    title: "이진 탐색 트리",
    chapter: "6장 탐색 트리",
    importanceLabel: "보통",
    importanceNote: "삽입·삭제 과정 출제",
    summary: "왼쪽 서브트리는 작고 오른쪽 서브트리는 큰 키를 저장해 평균 O(log n) 탐색을 지원합니다.",
    concepts: [
      { title: "BST 정렬 조건", description: "각 노드 기준으로 왼쪽 키는 작고 오른쪽 키는 큽니다." },
      { title: "탐색과 삽입", description: "키 비교 결과에 따라 왼쪽 또는 오른쪽으로 내려가며 빈 위치에 삽입합니다." },
      { title: "삭제의 세 경우", description: "리프, 자식 하나, 자식 둘인 경우를 나누고 자식 둘이면 후계자로 대체합니다." },
    ],
    examTip: "삽입 순서에 따라 완성되는 트리 모양과 삭제 후 대체 노드를 묻는 문제를 대비하세요.",
    characterComment: "작으면 왼쪽, 크면 오른쪽. 길을 잃지 말자!",
  },
  {
    id: 7,
    title: "그래프 순회",
    chapter: "7장 그래프",
    importanceLabel: "핵심만",
    importanceNote: "BFS·DFS 비교 출제",
    summary: "DFS는 스택·재귀로 깊게, BFS는 큐로 가까운 정점부터 순회합니다.",
    concepts: [
      { title: "깊이 우선 탐색(DFS)", description: "한 경로를 끝까지 탐색한 뒤 되돌아오며 스택 또는 재귀를 사용합니다." },
      { title: "너비 우선 탐색(BFS)", description: "시작점과 가까운 정점부터 탐색하며 큐를 사용합니다." },
      { title: "방문 배열", description: "이미 방문한 정점을 표시해 사이클에서 무한 반복하지 않도록 합니다." },
    ],
    examTip: "인접 정점 방문 순서를 고정한 뒤 DFS와 BFS 결과를 각각 직접 적어보세요.",
    characterComment: "마지막 STEP! DFS와 BFS 도구만 구분하면 끝이야.",
  },
];
