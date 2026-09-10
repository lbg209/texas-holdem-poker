// backend RoomStateResponse 및 관련 DTO와 1:1 대응하는 타입.
// 필드명/nullable 여부를 백엔드 코드(room.controller.dto.*) 그대로 옮긴다.

export type Phase = 'PREFLOP' | 'FLOP' | 'TURN' | 'RIVER' | 'SHOWDOWN';

export type PlayerStatus = 'ACTIVE' | 'FOLDED' | 'ALL_IN' | 'BUSTED';

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
  ready: boolean;
  autoFolded: boolean;
  // "나가기"를 예약했는지 — true면 핸드가 끝나는 즉시(또는 핸드 진행 중이 아니면 바로) 방에서 제거된다.
  leaving: boolean;
}

export interface ShowdownHandView {
  playerId: string;
  // 헤즈업 쇼다운에서 이 사람이 아직 공개 전(대기 중)이거나 머크했으면 null — isWinner는 그와
  // 무관하게 항상 실제 결과를 반영한다(카드는 안 보여도 승리 배지/칩 이동은 정상 동작해야 하므로).
  handRank: HandRank | null;
  bestFive: CardView[] | null;
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
  winnerId: string | null;
  // winnerId와 같은 시점에 고정된 닉네임 — 승자가 GAME OVER 카운트다운 도중 나가도 정확히 표시된다.
  winnerNickname: string | null;
  turnDeadlineAtMillis: number | null;
  nextHandAtMillis: number | null;
  foldWinWinnerId: string | null;
  // 헤즈업 쇼다운에서 공개/머크를 결정해야 하는 사람의 id. 아직 결정 전(대기 중)에만 non-null.
  headsUpDeciderPlayerId: string | null;
  headsUpRevealDeadlineAtMillis: number | null;
  // 이번 핸드에서 카드가 보이기로 확정된 사람들의 id 목록(폴드승 자원 공개 + 헤즈업 자동/자원/강제 공개).
  revealedPlayerIds: string[];
  // GAME OVER(winnerId non-null) 상태에서 방이 자동으로 초기화되는 시각. GAME OVER가 아니면 null.
  gameOverResetAtMillis: number | null;
  // 이번 방의 설정값. 방이 비어있을 때 "방 만들기"로 바꿀 수 있고, 그 전까지는 서버 기본값이다.
  startingChips: number;
  smallBlind: number;
  bigBlind: number;
  maxPlayers: number;
  // 방 정체성(로비/좌측 정보 패널 표시용).
  roomCode: string;
  name: string;
  isPrivate: boolean;
}

export interface JoinPlayerResponse {
  playerId: string;
}

export interface CreateRoomResponse {
  roomCode: string;
}

// 로비의 방 목록 한 줄에 대응한다. 비공개방은 이 목록 자체에 나타나지 않는다(백엔드가 걸러서 내려줌).
export interface RoomSummaryView {
  roomCode: string;
  name: string;
  isPrivate: boolean;
  playerCount: number;
  maxPlayers: number;
  inProgress: boolean;
}

export interface ErrorResponse {
  message: string;
}
