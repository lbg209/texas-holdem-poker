import type { PlayerActionType, PlayerView, RoomStateResponse } from '../types/room';

// 테이블 위에서 재생할 시각 이벤트. 큐가 현재 어떤 연출을 보여주고 있는지를 나타내며,
// FlyingChips/DealingCards 같은 오버레이 컴포넌트가 이 값을 보고 무엇을 어디로 움직일지 결정한다.
export type ActiveVisualEvent =
  | { type: 'DEAL_CARD'; playerId: string; sequence: number }
  | { type: 'CHIPS_TO_POT'; contributions: { playerId: string; amount: number }[] }
  | { type: 'BLIND_FLOURISH'; contributions: { playerId: string; amount: number }[] }
  | { type: 'POT_TO_WINNERS'; winnerPlayerIds: string[]; reason: 'FOLD' | 'SHOWDOWN' };

export interface AnimationStep {
  visualEvent: ActiveVisualEvent | null;
  durationMs: number;
  patch: (state: RoomStateResponse) => RoomStateResponse;
  // 주어지면 이 스텝이 재생되는 동안 딜링 중 좌석별 "몇 장 뒷면으로 놓였는지" 카운터를 이 값으로
  // 바꾼다. undefined면 건드리지 않는다(대부분의 스텝). 실제 홀카드 데이터(RoomStateResponse)와는
  // 별개의, 순전히 연출용 상태다 — 상대 홀카드는 서버가 애초에 안 보내주기 때문에 여기서 따로 센다.
  dealProgress?: Record<string, number> | null;
}

const DEAL_CARD_MS = 200;
const HOLE_CARD_COUNT = 2;
// PlayerSeat의 HoleCard 뒤집기 연출(딜레이 150ms + 뒤집기 500ms = 650ms)이 다 끝날 시간을 준다.
const REVEAL_MY_CARDS_MS = 650;
const BLIND_FLOURISH_MS = 600;
const ACTION_HOLD_MS = 900;
const CHIPS_HOLD_MS = 800;
const CARD_INTERVAL_MS = 500;
const POT_TO_WINNER_MS = 500;
// CommunityCards의 RevealCard가 턴/리버에 주는 딜레이(500ms)+뒤집기(800ms) 연출이 다 끝날 시간을 준다.
const SLOW_CARD_STEP_MS = 1400;
const FLOP_CARD_COUNT = 3;
// 쇼다운에서 상대 홀카드를 한 장씩 뒤집어 공개하는 연출의 카드 한 장당 대기 시간(딜레이+뒤집기 포함).
const HOLE_CARD_REVEAL_STEP_MS = 900;

// 폴드로 조기 종료된 핸드는 showdownHands가 없다(카드 비교가 없었으므로) — 이 경우 마지막까지
// 폴드하지 않은 한 명이 승자다. 실제 쇼다운이면 서버가 내려준 승자 정보를 그대로 쓴다.
export function resolveHandWinners(state: RoomStateResponse): string[] {
  if (state.phase !== 'SHOWDOWN') {
    return [];
  }
  if (state.showdownHands) {
    return state.showdownHands.filter((h) => h.isWinner).map((h) => h.playerId);
  }
  // BUSTED는 이번 핸드에 아예 참여하지 않은 좌석이라 "폴드하지 않음" 조건만으로는 승자에
  // 잘못 포함될 수 있다(파산 상태는 FOLDED가 아니므로) — 명시적으로 제외한다.
  return state.players.filter((p) => p.status !== 'FOLDED' && p.status !== 'BUSTED').map((p) => p.id);
}

function findPlayer(players: PlayerView[], id: string): PlayerView | undefined {
  return players.find((p) => p.id === id);
}

// 버튼 다음 좌석부터 시계방향으로 딜링 순서를 만든다. 파산(BUSTED)한 좌석은 이번 핸드에
// 홀카드를 받지 않으므로(백엔드가 애초에 안 보냄) 딜링 연출 대상에서도 제외한다.
function dealOrder(players: PlayerView[], buttonPosition: number): PlayerView[] {
  const count = players.length;
  const order: PlayerView[] = [];
  for (let i = 1; i <= count; i++) {
    const player = players[(buttonPosition + i) % count];
    if (player.status !== 'BUSTED') {
      order.push(player);
    }
  }
  return order;
}

// 새 핸드가 시작될 때(쇼다운/빈 상태 -> PREFLOP): 카드 한 장씩 두 바퀴 딜링 -> 내 카드만 리빌 ->
// 블라인드 칩 그림자가 중앙으로 날아가는 연출, 순서대로. 이 전환에서는 다른 이벤트(액션/스트리트
// 진행/핸드종료)가 동시에 생길 일이 없으므로, 이 시퀀스가 그 diff 사이클의 전부가 된다.
function dealNewHandSteps(next: RoomStateResponse): AnimationStep[] {
  const steps: AnimationStep[] = [];
  const order = dealOrder(next.players, next.dealerButtonPosition);
  const dealtSoFar: Record<string, number> = {};
  for (const p of next.players) {
    dealtSoFar[p.id] = 0;
  }

  // 첫 딜링 스텝에서 커뮤니티 카드/팟/버튼 위치 등은 새 핸드 기준으로 바로 스냅하되(옛 핸드의
  // 카드/팟이 잠깐이라도 남아 보이지 않도록), 홀카드는 전부 비우고(뒷면 자리만 dealProgress로
  // 보여줌) currentActorId/currentBet/minimumRaise는 블라인드 연출까지 끝날 때까지 얼려둔다.
  let sequence = 0;
  let firstStep = true;
  for (let round = 0; round < HOLE_CARD_COUNT; round++) {
    for (const player of order) {
      dealtSoFar[player.id] += 1;
      const dealProgressSnapshot = { ...dealtSoFar };
      const isFirst = firstStep;
      firstStep = false;
      steps.push({
        visualEvent: { type: 'DEAL_CARD', playerId: player.id, sequence: sequence++ },
        durationMs: DEAL_CARD_MS,
        dealProgress: dealProgressSnapshot,
        patch: (state) =>
          isFirst
            ? {
                ...next,
                players: next.players.map((p) => ({ ...p, holeCards: [] })),
                currentActorId: null,
                currentBet: null,
                minimumRaise: null,
              }
            : state,
      });
    }
  }

  // 딜링이 끝나면 내 홀카드만(서버가 애초에 나에게만 실제 카드를 보내주므로, holeCards가 채워져
  // 있는 사람 = 나) 실제 데이터로 채워서 뒤집어 확인한다. 상대는 원래도 계속 뒷면이라 그대로 둔다.
  steps.push({
    visualEvent: null,
    durationMs: REVEAL_MY_CARDS_MS,
    dealProgress: null,
    patch: (state) => ({
      ...state,
      players: state.players.map((p) => {
        const np = findPlayer(next.players, p.id);
        return np && np.holeCards.length > 0 ? { ...p, holeCards: np.holeCards } : p;
      }),
    }),
  });

  // 블라인드를 낸 사람(이 시점에 currentRoundBet > 0인 사람 = SB/BB)의 칩 더미에서 그림자
  // 복사본이 중앙으로 날아갔다 사라진다. 실제 currentRoundBet/pots/chips는 건드리지 않는다 —
  // 진짜 스윕은 프리플랍 베팅이 끝났을 때 기존 로직이 처리한다.
  const blindContributions = next.players
    .filter((p) => p.currentRoundBet > 0)
    .map((p) => ({ playerId: p.id, amount: p.currentRoundBet }));

  if (blindContributions.length > 0) {
    steps.push({
      visualEvent: { type: 'BLIND_FLOURISH', contributions: blindContributions },
      durationMs: BLIND_FLOURISH_MS,
      patch: (state) => state,
    });
  }

  return steps;
}

// 직전에 화면에 반영된 상태(prev)와 서버가 보낸 최신 상태(next)를 비교해서, 순서대로 재생할
// 애니메이션 스텝 목록을 만든다. 스텝이 없으면(예: 새 핸드 시작 이전의 다른 사소한 변화) 호출
// 측에서 next로 즉시 스냅한다.
export function deriveSteps(prev: RoomStateResponse, next: RoomStateResponse): AnimationStep[] {
  if (prev.phase !== 'PREFLOP' && next.phase === 'PREFLOP') {
    return dealNewHandSteps(next);
  }

  const steps: AnimationStep[] = [];

  // 1) 누군가 새로 액션했으면(폴드 포함) 그 결과(액션 라벨/베팅액/칩)를 먼저 보여주고 잠깐 유지한다.
  //    "누가 액션했는지"는 next의 lastAction 변화로 비교하지 않는다 — 그 액션이 베팅 라운드를 끝내는
  //    경우(마지막 콜/체크 등)에는 서버가 같은 응답 안에서 다음 스트리트로 넘기며 전원의 lastAction을
  //    이미 null로 리셋해버려서, next에는 그 액션의 흔적이 지워진 채로 온다. 대신 prev.currentActorId
  //    ("직전까지 누구 차례였는지")는 그 리셋과 무관하게 이번 diff에서 액션한 사람을 정확히 알려준다.
  const actorId = prev.currentActorId;
  const actedIds = actorId !== null && findPlayer(next.players, actorId) ? [actorId] : [];

  // 액션한 사람의 "액션 직후 & 리셋되기 전" 베팅액/라벨을 totalHandContribution 증가분으로 역산한다.
  // lastAction이 리셋으로 사라졌다면(체크/콜이 라운드를 끝낸 경우) 증가분 유무로 CHECK/CALL을 추정해
  // 채워 넣는다 — 폴드/올인은 status만으로 이미 정확한 라벨이 나오므로 건드리지 않는다.
  const postActionBets = new Map<string, number>();
  const reconstructedLastAction = new Map<string, PlayerActionType | null>();
  for (const np of next.players) {
    const pp = findPlayer(prev.players, np.id);
    if (!pp) {
      continue;
    }
    if (actedIds.includes(np.id)) {
      const contributedThisAction = np.totalHandContribution - pp.totalHandContribution;
      postActionBets.set(np.id, pp.currentRoundBet + contributedThisAction);
      if (np.lastAction !== null) {
        reconstructedLastAction.set(np.id, np.lastAction);
      } else if (np.status !== 'FOLDED' && np.status !== 'ALL_IN') {
        reconstructedLastAction.set(np.id, contributedThisAction > 0 ? 'CALL' : 'CHECK');
      }
    } else {
      postActionBets.set(np.id, pp.currentRoundBet);
    }
  }

  if (actedIds.length > 0) {
    steps.push({
      visualEvent: null,
      durationMs: ACTION_HOLD_MS,
      patch: (state) => ({
        ...state,
        players: state.players.map((p) => {
          if (!actedIds.includes(p.id)) {
            return p;
          }
          const np = findPlayer(next.players, p.id)!;
          return {
            ...p,
            lastAction: reconstructedLastAction.get(p.id) ?? np.lastAction,
            currentRoundBet: postActionBets.get(p.id) ?? np.currentRoundBet,
            totalHandContribution: np.totalHandContribution,
            chips: np.chips,
            status: np.status,
          };
        }),
      }),
    });
  }

  // 2) 스트리트가 넘어가며 베팅액이 팟으로 쓸려 들어갔으면(0으로 리셋) 칩 이동을 보여준다.
  //    폴드로 조기 종료된 경우는 서버가 베팅액을 리셋하지 않으므로 이 단계는 자연스럽게 생략된다.
  //    액션한 사람도 postActionBets(위에서 역산한, 스윕 직전 베팅액)을 기준으로 판단해야
  //    "마지막 콜과 동시에 스윕된" 경우도 그 사람의 더미가 다른 사람들과 함께 이동한다.
  const contributions = prev.players
    .map((p) => ({ playerId: p.id, amount: postActionBets.get(p.id) ?? p.currentRoundBet }))
    .filter((c) => c.amount > 0)
    .filter((c) => findPlayer(next.players, c.playerId)?.currentRoundBet === 0);

  if (contributions.length > 0) {
    steps.push({
      visualEvent: { type: 'CHIPS_TO_POT', contributions },
      durationMs: CHIPS_HOLD_MS,
      patch: (state) => ({
        ...state,
        players: state.players.map((p) => {
          const np = findPlayer(next.players, p.id)!;
          return { ...p, currentRoundBet: np.currentRoundBet, totalHandContribution: np.totalHandContribution };
        }),
        pots: next.pots,
      }),
    });
  }

  // 3) 새 커뮤니티 카드는 한 장씩 순서대로 딜인한다. 턴/리버(인덱스 3, 4)는 CommunityCards의
  //    RevealCard가 뒷면->앞면 전환을 더 길게 재생하므로(긴장감 연출), 그 연출이 끝나기 전에
  //    다음 스텝(핸드 종료 등)으로 안 넘어가도록 이 스텝의 대기 시간도 맞춰서 늘린다.
  if (next.communityCards.length > prev.communityCards.length) {
    const revealedCount = prev.communityCards.length;
    const newCount = next.communityCards.length - revealedCount;
    for (let i = 1; i <= newCount; i++) {
      const newCardIndex = revealedCount + i - 1;
      const isSlow = newCardIndex >= FLOP_CARD_COUNT;
      steps.push({
        visualEvent: null,
        durationMs: isSlow ? SLOW_CARD_STEP_MS : CARD_INTERVAL_MS,
        patch: (state) => ({
          ...state,
          communityCards: next.communityCards.slice(0, revealedCount + i),
        }),
      });
    }
  }

  // 4) 핸드 종료 — 폴드 종료(showdownHands 없음)와 실제 쇼다운(showdownHands 있음)을 구분해서
  //    팟이 정확한 승자에게 이동하는 연출을 재생한다.
  if (prev.phase !== 'SHOWDOWN' && next.phase === 'SHOWDOWN') {
    const winnerIds = resolveHandWinners(next);
    const reason: 'FOLD' | 'SHOWDOWN' = next.showdownHands ? 'SHOWDOWN' : 'FOLD';

    // 실제 쇼다운이면, 승자 하이라이트가 뜨기 전에 상대의 홀카드를 한 장씩 순서대로 공개한다.
    // 내 카드는 원래부터 보이고 있었으므로(0장 -> 2장으로 바뀌는 대상이 아니라서) 자연히 제외된다.
    if (reason === 'SHOWDOWN') {
      const revealTargets: { playerId: string; revealedCount: number }[] = [];
      for (const np of next.players) {
        const pp = findPlayer(prev.players, np.id);
        if (pp && pp.holeCards.length === 0 && np.holeCards.length > 0) {
          for (let revealedCount = 1; revealedCount <= np.holeCards.length; revealedCount++) {
            revealTargets.push({ playerId: np.id, revealedCount });
          }
        }
      }

      for (const target of revealTargets) {
        steps.push({
          visualEvent: null,
          durationMs: HOLE_CARD_REVEAL_STEP_MS,
          patch: (state) => ({
            ...state,
            players: state.players.map((p) => {
              if (p.id !== target.playerId) {
                return p;
              }
              const np = findPlayer(next.players, p.id)!;
              return { ...p, holeCards: np.holeCards.slice(0, target.revealedCount) };
            }),
          }),
        });
      }
    }

    steps.push({
      visualEvent: { type: 'POT_TO_WINNERS', winnerPlayerIds: winnerIds, reason },
      durationMs: POT_TO_WINNER_MS,
      patch: () => next,
    });
  }

  return steps;
}
