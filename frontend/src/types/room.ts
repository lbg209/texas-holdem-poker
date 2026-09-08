// backend RoomStateResponse 및 관련 DTO와 1:1 대응하는 타입.
// 필드명/nullable 여부를 백엔드 코드(room.controller.dto.*) 그대로 옮긴다.

export type Phase = 'PREFLOP' | 'FLOP' | 'TURN' | 'RIVER' | 'SHOWDOWN';

export type PlayerStatus = 'ACTIVE' | 'FOLDED' | 'ALL_IN';

export type PlayerActionType = 'CHECK' | 'CALL' | 'BET' | 'RAISE' | 'FOLD' | 'ALL_IN';

export type HandRank =
  | 'HIGH_CARD'
  | 'ONE_PAIR'
  | 'TWO_PAIR'
  | 'THREE_OF_A_KIND'
  | 'STRAIGHT'
  | 'FLUSH'
  | 'FULL_HOUSE'
  | 'FOUR_OF_A_KIND'
  | 'STRAIGHT_FLUSH';

export interface CardView {
  suit: string;
  rank: string;
  display: string;
}

export interface PotView {
  amount: number;
  eligiblePlayerIds: string[];
}

export interface PlayerView {
  id: string;
  nickname: string;
  chips: number;
  status: PlayerStatus;
  currentRoundBet: number;
  totalHandContribution: number;
  lastAction: PlayerActionType | null;
  netChipChange: number;
  holeCards: CardView[];
}

export interface ShowdownHandView {
  playerId: string;
  handRank: HandRank;
  bestFive: CardView[];
  isWinner: boolean;
}

export interface RoomStateResponse {
  phase: Phase | null;
  communityCards: CardView[];
  pots: PotView[];
  players: PlayerView[];
  dealerButtonPosition: number;
  currentBet: number | null;
  minimumRaise: number | null;
  currentActorId: string | null;
  showdownHands: ShowdownHandView[] | null;
}

export interface JoinPlayerResponse {
  playerId: string;
}

export interface ErrorResponse {
  message: string;
}
