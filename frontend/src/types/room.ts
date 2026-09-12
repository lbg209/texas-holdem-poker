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
  // 방장(방을 만든 뒤 가장 먼저 입장했거나, 그 방장이 나가서 승계받은 사람)인지 — 순수 표시용
  // 배지 + 강퇴 버튼 노출 여부 판단에 쓰인다.
  isOwner: boolean;
  // 고정 좌석제의 물리적 좌석 번호(0~maxPlayers-1). 빈 좌석을 가려내고, 빈자리 클릭 시
  // 입장/이동 API에 그대로 실어 보내는 데 쓰인다.
  seatIndex: number;
}

export interface ShowdownHandView {
  playerId: string;
  // 헤즈업 쇼다운에서 이 사람이 아직 공개 전(대기 중)이거나 머크했으면 null — isWinner는 그와
  // 무관하게 항상 실제 결과를 반영한다(카드는 안 보여도 승리 배지/칩 이동은 정상 동작해야 하므로).
  handRank: HandRank | null;
  bestFive: CardView[] | null;
  isWinner: boolean;
}

// holeCards/handRank/bestFive는 본인이거나 그 핸드에서 실제로 공개됐던 경우에만 채워진다 —
// 머크했으면 지난 핸드라도 영원히 비어 있다(실시간 쇼다운 공개 규칙과 동일).
export interface HandHistoryHandView {
  playerId: string;
  nickname: string;
  holeCards: CardView[];
  handRank: HandRank | null;
  bestFive: CardView[] | null;
  isWinner: boolean;
}

export interface HandHistoryPotView {
  amount: number;
  winnerIds: string[];
}

// wonByFold=true면 hands가 비어 있고(실제 쇼다운이 없었으므로) foldWinWinnerId/Nickname만 채워진다.
export interface HandHistoryEntryView {
  handNumber: number;
  communityCards: CardView[];
  pots: HandHistoryPotView[];
  hands: HandHistoryHandView[];
  wonByFold: boolean;
  foldWinWinnerId: string | null;
  foldWinWinnerNickname: string | null;
}

export interface BlindLevelView {
  level: number;
  smallBlind: number;
  bigBlind: number;
  ante: number;
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
  // 판 수 기준 블라인드 상승이 마지막 단계에 도달하면 걷는 앤티(빅블라인드 앤티 방식 — 버튼만
  // 혼자 냄). 그 전까지는 0.
  ante: number;
  // 1부터 시작하는 현재 블라인드 레벨 번호, 블라인드 리셋(방 시작/GAME OVER 리매치) 이후 지금까지
  // 끝난 핸드 수, 전체 블라인드 구조표(고정 6단계, 몇 판째인지와 무관하게 항상 동일).
  currentBlindLevel: number;
  handsSinceBlindReset: number;
  blindStructure: BlindLevelView[];
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
