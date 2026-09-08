import type { RoomStateResponse } from '../types/room';

export interface LegalActions {
  canFold: boolean;
  canCheck: boolean;
  canCall: boolean;
  callAmount: number;
  canBet: boolean;
  canRaise: boolean;
  canAllIn: boolean;
  minTotal: number;
  maxTotal: number;
}

// 서버(BettingRound)가 최종 검증을 하므로, 여기서는 지금 노출된 RoomStateResponse만으로
// "대략 가능한 액션"을 계산한다. short all-in 이후 재오픈된 경우(RAISE 금지) 같은 서버 내부
// 상태는 API에 없어 반영하지 못한다 — 그 경우 RAISE를 시도하면 서버가 거부하고 ERROR로 응답한다.
export function computeLegalActions(roomState: RoomStateResponse, myPlayerId: string | null): LegalActions | null {
  if (!myPlayerId || roomState.currentActorId !== myPlayerId) {
    return null;
  }
  const me = roomState.players.find((p) => p.id === myPlayerId);
  if (!me) {
    return null;
  }

  const currentBet = roomState.currentBet ?? 0;
  const minimumRaise = roomState.minimumRaise ?? 0;
  const maxTotal = me.currentRoundBet + me.chips;

  const canCheck = me.currentRoundBet === currentBet;
  const canCall = !canCheck;
  const callAmount = Math.min(currentBet - me.currentRoundBet, me.chips);
  const canBet = currentBet === 0 && me.chips > 0;
  const canRaise = currentBet > 0 && maxTotal > currentBet && me.chips > 0;
  const canAllIn = me.chips > 0;

  return {
    canFold: true,
    canCheck,
    canCall,
    callAmount,
    canBet,
    canRaise,
    canAllIn,
    minTotal: currentBet + minimumRaise,
    maxTotal,
  };
}
