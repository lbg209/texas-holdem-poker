export type Position = 'BTN' | 'SB' | 'BB' | 'UTG' | 'UTG+1' | 'UTG+2' | 'LJ' | 'HJ' | 'CO';

// 버튼 기준 시계방향 표준 순서(9-max 풀링 기준). 지금 백엔드 Room.MAX_PLAYERS는 6이라 실제로는
// UTG/HJ/CO까지만 쓰이지만, 나중에 정원이 늘어나도 이 배열만 늘리면 되고 계산 로직은 그대로 재사용된다.
// 인원이 이 목록 길이보다 적으면 버튼에 가까운 자리(CO/HJ...)는 유지하고, 초반 자리(UTG+1/UTG+2/LJ 등)를
// UTG 하나로 압축한다 — 인원이 줄어도 실제 포커에서 CO/HJ 같은 후반 자리가 마지막까지 남는 것과 동일한 관행.
const FULL_RING_MIDDLE: Position[] = ['UTG', 'UTG+1', 'UTG+2', 'LJ', 'HJ', 'CO'];

function pickMiddlePositions(needed: number): Position[] {
  if (needed <= 0) {
    return [];
  }
  if (needed >= FULL_RING_MIDDLE.length) {
    return FULL_RING_MIDDLE;
  }
  const tailCount = needed - 1;
  const tail = tailCount > 0 ? FULL_RING_MIDDLE.slice(FULL_RING_MIDDLE.length - tailCount) : [];
  return ['UTG', ...tail];
}

// seatIndex 좌석의 포지션을 계산한다. offset은 GameEngine.playerAtOffset()과 동일한 기준
// (버튼=0, SB=1, BB=2, ...) 이라 백엔드의 블라인드/첫 액션 순서와 항상 일치한다.
export function computePosition(seatIndex: number, dealerButtonPosition: number, playerCount: number): Position | null {
  if (dealerButtonPosition === -1 || playerCount < 2) {
    return null;
  }
  const offset = (seatIndex - dealerButtonPosition + playerCount) % playerCount;

  if (playerCount === 2) {
    // 헤즈업: 버튼이 곧 SB (GameEngine.postBlinds의 headsUp 분기와 동일)
    return offset === 0 ? 'BTN' : 'BB';
  }
  if (offset === 0) return 'BTN';
  if (offset === 1) return 'SB';
  if (offset === 2) return 'BB';

  const middle = pickMiddlePositions(playerCount - 3);
  return middle[offset - 3];
}
