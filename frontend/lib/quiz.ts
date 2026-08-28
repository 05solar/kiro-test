export type Question = {
  id: number;
  question: string;
  options: string[];
  answerIndex: number;
  explanation: string;
};

/** STEP당 정확히 이만큼의 문제를 유지해야 한다. */
export const QUESTIONS_PER_STEP = 5;

export const MOCK_QUIZZES: Record<number, Question[]> = {
  1: [
    { id: 101, question: "스택(Stack)의 기본 동작 방식은?", options: ["FIFO", "LIFO", "우선순위 순서", "무작위 순서"], answerIndex: 1, explanation: "스택은 가장 나중에 넣은 원소를 먼저 꺼내는 LIFO 구조입니다." },
    { id: 102, question: "스택에 원소를 삽입하는 연산은?", options: ["push", "pop", "dequeue", "peekRear"], answerIndex: 0, explanation: "push는 top 위치에 새 원소를 삽입합니다." },
    { id: 103, question: "크기 N인 배열 스택의 포화 조건은?", options: ["top == 0", "top == N", "top == N - 1", "top == -1"], answerIndex: 2, explanation: "배열 인덱스가 0부터 시작하므로 마지막 위치는 N-1입니다." },
    { id: 104, question: "스택에 1, 2, 3을 순서대로 push한 뒤 pop을 두 번 호출하면, 이후 top에 남는 값은?", options: ["1", "2", "3", "스택이 빈다"], answerIndex: 0, explanation: "push 순서대로 쌓이면 top부터 3, 2, 1이 되고 pop을 두 번 하면 3과 2가 제거되어 1만 남습니다." },
    { id: 105, question: "빈 스택에서 pop을 호출하면 어떤 일이 일어나나?", options: ["0을 반환한다", "가장 최근에 pop된 값을 다시 반환한다", "언더플로 오류(예외)가 발생한다", "자동으로 push된다"], answerIndex: 2, explanation: "빈 스택에서 pop을 시도하면 꺼낼 원소가 없어 언더플로 오류가 발생합니다. 0이나 이전 값을 반환한다고 착각하기 쉬운 함정입니다." },
  ],
  2: [
    { id: 201, question: "중위 표기식 A+B*C의 후위 표기식은?", options: ["AB+C*", "ABC*+", "+A*BC", "AB*C+"], answerIndex: 1, explanation: "곱셈을 먼저 처리하므로 B C * 뒤에 A와의 +가 옵니다." },
    { id: 202, question: "중위식을 후위식으로 바꿀 때 스택에 주로 저장하는 것은?", options: ["피연산자", "연산자", "결과값", "변수 이름"], answerIndex: 1, explanation: "연산자를 스택에 보관하며 우선순위를 비교합니다." },
    { id: 203, question: "후위 표기식 계산 중 연산자를 만나면 먼저 할 일은?", options: ["연산자를 출력한다", "피연산자 두 개를 pop한다", "스택을 비운다", "연산자를 push한다"], answerIndex: 1, explanation: "피연산자 두 개를 꺼내 계산한 결과를 다시 push합니다." },
    { id: 204, question: "전위(prefix), 중위(infix), 후위(postfix) 표기법 중 연산자가 항상 두 피연산자 뒤에 위치하는 것은?", options: ["전위", "중위", "후위", "세 표기법 모두 동일"], answerIndex: 2, explanation: "후위 표기법은 연산자를 두 피연산자 뒤에 적는 방식입니다." },
    { id: 205, question: "A*B+C의 후위 표기식은?", options: ["AB*C+", "ABC+*", "A*BC+", "AB+C*"], answerIndex: 0, explanation: "곱셈의 우선순위가 높아 A*B를 먼저 AB*로 바꾼 뒤 마지막에 +C를 붙입니다. 연산자를 등장 순서대로만 나열하면 ABC+*처럼 틀리기 쉽습니다." },
  ],
  3: [
    { id: 301, question: "큐(Queue)의 동작 방식은?", options: ["LIFO", "FIFO", "우선순위만 사용", "임의 삭제"], answerIndex: 1, explanation: "먼저 들어온 원소가 먼저 나가는 FIFO 구조이며, 삽입은 rear에서 삭제는 front에서 이루어집니다." },
    { id: 302, question: "배열 큐 대신 원형 큐를 쓰는 이유는?", options: ["정렬 속도 향상", "앞쪽 빈 공간 재사용", "중복 제거", "탐색을 O(1)로 변경"], answerIndex: 1, explanation: "선형 배열 큐는 dequeue 후 앞쪽 빈 공간을 재사용하지 못해 낭비가 발생하는데, 원형 큐는 나머지 연산으로 이 공간을 재사용합니다." },
    { id: 303, question: "크기 N인 원형 큐의 포화 조건은?", options: ["(rear + 1) % N == front", "front == rear", "rear == N - 1", "front > rear"], answerIndex: 0, explanation: "공백과 포화 상태를 구분하기 위해 한 칸을 비워두므로, 다음 rear 위치가 front와 같아지면 포화 상태입니다." },
    { id: 304, question: "중위 표기식을 후위 표기식으로 바꿀 때 주로 쓰는 자료구조는?", options: ["큐", "스택", "트리", "해시 테이블"], answerIndex: 1, explanation: "연산자를 스택에 쌓아 우선순위를 비교하면서 피연산자를 그대로 출력하는 방식으로 변환합니다." },
    { id: 305, question: "연결 리스트 큐에서 enqueue를 O(1)로 만들려면?", options: ["rear 포인터를 함께 유지한다", "매번 배열로 복사한다", "노드를 정렬해 둔다", "해시 함수를 쓴다"], answerIndex: 0, explanation: "rear 포인터를 유지하면 리스트 끝을 찾기 위한 순회 없이 바로 삽입할 수 있어 enqueue가 O(1)이 됩니다." },
  ],
  4: [
    { id: 401, question: "덱(Deque)에서 가능한 연산은?", options: ["rear 삽입만", "front 삭제만", "양쪽 삽입·삭제", "중간 원소만 삭제"], answerIndex: 2, explanation: "덱은 front와 rear 양쪽에서 삽입과 삭제가 가능합니다." },
    { id: 402, question: "우선순위 큐에서 먼저 삭제되는 원소는?", options: ["가장 먼저 들어온 원소", "가장 나중에 들어온 원소", "우선순위가 가장 높은 원소", "크기가 가장 작은 원소만"], answerIndex: 2, explanation: "도착 순서보다 지정된 우선순위를 기준으로 삭제합니다." },
    { id: 403, question: "우선순위 큐를 효율적으로 구현할 때 흔히 쓰는 자료구조는?", options: ["힙", "단순 스택", "원형 연결 리스트만", "해시 집합"], answerIndex: 0, explanation: "힙은 우선순위가 가장 높은 원소를 효율적으로 찾고 제거합니다." },
    { id: 404, question: "빈 덱에서 addFront(1), addRear(2), addFront(3) 순서로 연산을 수행했을 때, 덱의 앞(front)에서부터 나열한 값은?", options: ["3, 1, 2", "1, 2, 3", "2, 1, 3", "3, 2, 1"], answerIndex: 0, explanation: "addFront(1) → [1], addRear(2) → [1,2], addFront(3) → [3,1,2] 순으로 바뀌어 앞에서부터 3, 1, 2가 됩니다." },
    { id: 405, question: "우선순위 큐에서 두 원소의 우선순위가 같을 때 일반적으로 어떤 기준이 적용되나?", options: ["항상 무작위로 선택한다", "구현 방식에 따라 다르며 흔히 도착 순서를 보조 기준으로 쓴다", "항상 값이 큰 원소를 나중에 삭제한다", "동일 우선순위 원소는 삭제할 수 없다"], answerIndex: 1, explanation: "표준이 정해져 있지 않아 구현에 따라 다르며, 흔히 먼저 들어온 원소를 우선하는 방식을 보조 기준으로 사용합니다." },
  ],
  5: [
    { id: 501, question: "트리에서 자식이 없는 노드는?", options: ["루트", "리프", "부모", "간선"], answerIndex: 1, explanation: "자식이 없는 끝 노드를 리프(leaf) 노드라고 합니다." },
    { id: 502, question: "이진 트리에서 한 노드가 가질 수 있는 최대 자식 수는?", options: ["1", "2", "3", "제한 없음"], answerIndex: 1, explanation: "이진 트리의 각 노드는 최대 두 자식을 가집니다." },
    { id: 503, question: "노드의 차수(degree)는 무엇을 뜻하나?", options: ["부모의 수", "자식의 수", "전체 노드 수", "루트까지 거리"], answerIndex: 1, explanation: "노드에서 뻗어나가는 자식(서브트리)의 수가 차수입니다." },
    { id: 504, question: "노드가 7개인 포화 이진트리의 높이는? (루트의 높이를 0으로 정의)", options: ["1", "2", "3", "7"], answerIndex: 1, explanation: "포화 이진트리는 각 레벨이 가득 차 있어 2^(h+1)-1=7이 되는 h=2가 답입니다." },
    { id: 505, question: "완전 이진트리와 포화 이진트리의 관계에 대한 설명으로 옳은 것은?", options: ["포화 이진트리는 항상 완전 이진트리이다", "완전 이진트리는 항상 포화 이진트리이다", "두 개념은 서로 무관하다", "포화 이진트리는 리프가 2개뿐이다"], answerIndex: 0, explanation: "포화 이진트리(모든 레벨이 가득 찬 트리)는 완전 이진트리의 조건도 만족하지만, 완전 이진트리라고 해서 항상 포화 이진트리는 아닙니다." },
  ],
  6: [
    { id: 601, question: "이진 탐색 트리에서 현재 키보다 작은 키는 어디로 이동하나?", options: ["왼쪽", "오른쪽", "루트", "임의 위치"], answerIndex: 0, explanation: "BST는 작은 키를 왼쪽, 큰 키를 오른쪽 서브트리에 둡니다." },
    { id: 602, question: "균형 잡힌 BST의 평균 탐색 시간 복잡도는?", options: ["O(1)", "O(log n)", "O(n)", "O(n²)"], answerIndex: 1, explanation: "비교할 때마다 탐색 범위가 한 레벨씩 줄어 평균 O(log n)입니다." },
    { id: 603, question: "자식이 둘인 BST 노드를 삭제할 때 대체할 수 있는 것은?", options: ["임의의 리프", "중위 후계자", "항상 루트", "가장 깊은 노드"], answerIndex: 1, explanation: "오른쪽 서브트리의 최솟값인 중위 후계자 등으로 대체합니다." },
    { id: 604, question: "다음 순서로 삽입해 만든 이진 탐색 트리에서 루트의 오른쪽 자식은? 삽입 순서: 5, 2, 8, 1, 3", options: ["1", "2", "3", "8"], answerIndex: 3, explanation: "5가 루트이고 5보다 큰 8이 오른쪽에 삽입되어 루트의 오른쪽 자식이 됩니다." },
    { id: 605, question: "이진 탐색 트리(BST)에 대한 설명으로 옳은 것은?", options: ["삽입 순서와 무관하게 항상 균형을 유지한다", "편향된 트리에서는 최악의 경우 탐색이 O(n)이 될 수 있다", "왼쪽 서브트리의 키가 항상 더 크다", "삭제 연산은 지원하지 않는다"], answerIndex: 1, explanation: "일반 BST는 균형을 보장하지 않아 삽입 순서에 따라 편향될 수 있고, 이 경우 탐색이 O(n)까지 느려질 수 있습니다." },
  ],
  7: [
    { id: 701, question: "너비 우선 탐색(BFS)이 사용하는 자료구조는?", options: ["스택", "큐", "힙", "해시맵"], answerIndex: 1, explanation: "BFS는 먼저 발견한 정점부터 방문하기 위해 큐를 사용합니다." },
    { id: 702, question: "깊이 우선 탐색(DFS)을 구현할 수 있는 방식은?", options: ["큐만 사용", "스택 또는 재귀", "힙만 사용", "정렬 배열만 사용"], answerIndex: 1, explanation: "DFS는 되돌아갈 경로를 스택이나 재귀 호출 스택에 저장합니다." },
    { id: 703, question: "그래프 순회에서 방문 배열이 필요한 이유는?", options: ["정점을 정렬하려고", "사이클의 무한 반복을 막으려고", "간선 수를 늘리려고", "가중치를 바꾸려고"], answerIndex: 1, explanation: "이미 방문한 정점을 다시 탐색하지 않아 사이클에서도 종료할 수 있습니다." },
    { id: 704, question: "그래프의 인접 리스트가 A:[B,C], B:[A,D], C:[A,E], D:[B], E:[C]일 때, A에서 시작한 BFS 방문 순서는? (인접 정점은 알파벳 순으로 방문)", options: ["A, B, C, D, E", "A, B, D, C, E", "A, C, B, E, D", "A, B, C, E, D"], answerIndex: 0, explanation: "BFS는 큐를 사용해 A의 인접 정점 B, C를 먼저 큐에 넣고, 이어서 B의 인접 D, C의 인접 E를 방문하므로 A, B, C, D, E 순서가 됩니다." },
    { id: 705, question: "DFS와 BFS의 차이에 대한 설명으로 옳은 것은?", options: ["DFS와 BFS는 항상 동일한 방문 순서를 만든다", "BFS는 스택을 사용하고 DFS는 큐를 사용한다", "그래프 구조에 따라 DFS와 BFS의 방문 순서는 서로 다를 수 있다", "DFS는 반드시 재귀로만 구현할 수 있다"], answerIndex: 2, explanation: "두 알고리즘은 서로 다른 자료구조(스택/재귀 vs 큐)를 사용하므로 그래프 구조에 따라 방문 순서가 달라질 수 있습니다." },
  ],
};

// 개발 환경에서만 STEP별 문항 수가 정확히 QUESTIONS_PER_STEP개인지 검증한다.
if (process.env.NODE_ENV !== "production") {
  Object.entries(MOCK_QUIZZES).forEach(([stepId, questions]) => {
    if (questions.length !== QUESTIONS_PER_STEP) {
      console.warn(
        `[MOCK_QUIZZES] STEP ${stepId}가 ${questions.length}문제입니다. ${QUESTIONS_PER_STEP}문제가 되도록 채워주세요.`
      );
    }
    const emptyExplanation = questions.find((question) => !question.explanation.trim());
    if (emptyExplanation) {
      console.warn(`[MOCK_QUIZZES] STEP ${stepId}의 문제 id=${emptyExplanation.id}에 해설(explanation)이 비어 있습니다.`);
    }
  });
}

/** 추후 내부 구현만 API fetch로 교체할 수 있는 퀴즈 조회 경계. */
export async function getQuiz(stepId: number): Promise<Question[]> {
  return MOCK_QUIZZES[stepId] ?? [];
}
