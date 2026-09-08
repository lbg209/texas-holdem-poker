import type { CardView, PlayerView, ShowdownHandView } from '../../types/room';
import type { SeatPosition } from './computeSeatPositions';
import type { Position } from '../../lib/positions';
import { formatMoney } from '../../lib/formatMoney';
import { containsCard, getHandRankLabel, isSpecialHandRank } from '../../lib/handRank';
import { Card } from './Card';
import { ChipStack } from './ChipStack';

interface PlayerSeatProps {
  player: PlayerView;
  position: SeatPosition;
  tablePosition: Position | null;
  isMe: boolean;
  isCurrentActor: boolean;
  showCards: boolean;
  isChipLeader: boolean;
  showdownHand: ShowdownHandView | null;
  winnerCards: CardView[];
  handEnded: boolean;
}

// 액션 줄: FOLD/ALL-IN은 상태 기준(스트리트가 바뀌어도 유지), CHECK/CALL/BET/RAISE는
// 이번 스트리트의 lastAction 기준(스트리트 전환 시 서버에서 null로 리셋됨)으로 판단한다.
function getActionLine(player: PlayerView): string | null {
  if (player.status === 'FOLDED') {
    return 'FOLD';
  }
  if (player.status === 'ALL_IN') {
    return 'ALL-IN';
  }
  if (player.lastAction === 'CHECK') {
    return 'CHECK';
  }
  if (
    (player.lastAction === 'CALL' || player.lastAction === 'BET' || player.lastAction === 'RAISE') &&
    player.currentRoundBet > 0
  ) {
    return `${player.lastAction} ${formatMoney(player.currentRoundBet)}`;
  }
  return null;
}

export function PlayerSeat({
  player,
  position,
  tablePosition,
  isMe,
  isCurrentActor,
  showCards,
  isChipLeader,
  showdownHand,
  winnerCards,
  handEnded,
}: PlayerSeatProps) {
  const [hole0, hole1] = player.holeCards;
  const faceUp = player.holeCards.length > 0;
  const actionLine = getActionLine(player);
  const isWinner = showdownHand?.isWinner ?? false;
  const isSpecialHand = showdownHand ? isSpecialHandRank(showdownHand.handRank) : false;

  // 하이라이트는 "이긴 조합"만 표시한다 — 진 사람의 카드가 우연히 자기 최고 조합에 포함돼도
  // (예: 무관한 페어) 강조하지 않는다. 승자의 bestFive에 실제로 포함된 카드만 강조.
  const cards = showCards && (
    <div className="flex gap-1">
      <Card card={hole0} face={faceUp ? 'up' : 'down'} highlighted={!!(hole0 && containsCard(winnerCards, hole0))} />
      <Card card={hole1} face={faceUp ? 'up' : 'down'} highlighted={!!(hole1 && containsCard(winnerCards, hole1))} />
    </div>
  );

  const info = (
    <div
      className={`flex min-w-28 flex-col items-center rounded px-2 py-1 text-xs shadow-md sm:text-sm ${
        isWinner
          ? 'border-2 border-yellow-400 bg-gradient-to-b from-yellow-900/60 to-slate-900 shadow-[0_0_12px_rgba(250,204,21,0.6)]'
          : isCurrentActor
            ? 'border-2 border-emerald-400 bg-gradient-to-b from-slate-700 to-slate-900 shadow-emerald-400/40'
            : 'border border-slate-600 bg-gradient-to-b from-slate-700/90 to-slate-900/90'
      } ${player.status === 'FOLDED' ? 'opacity-50' : ''}`}
    >
      <span className={`font-medium ${isMe ? 'text-emerald-400' : 'text-slate-100'}`}>
        {isChipLeader && <span title="칩리더">👑</span>}
        {player.nickname}
        {tablePosition && <span className="ml-1 rounded bg-slate-200 px-1 text-[10px] text-slate-900">{tablePosition}</span>}
      </span>
      {actionLine && <span className="text-amber-300">{actionLine}</span>}
      {player.totalHandContribution > 0 && (
        <span className="text-amber-500/80">누적 {formatMoney(player.totalHandContribution)}</span>
      )}
      {showdownHand && (
        <span
          className={
            isSpecialHand
              ? 'bg-gradient-to-r from-yellow-300 via-amber-200 to-yellow-400 bg-clip-text font-bold text-transparent drop-shadow'
              : 'font-medium text-sky-300'
          }
        >
          {isSpecialHand && '✨ '}
          {getHandRankLabel(showdownHand.handRank, showdownHand.bestFive)}
        </span>
      )}
      {isWinner && <span className="font-bold text-yellow-400">WINNER</span>}
      {isCurrentActor && <span className="font-semibold text-emerald-400">차례</span>}
    </div>
  );

  // 카드는 항상 테이블 라인(좌석 좌표) 위에 오고, 유저 정보는 그보다 바깥쪽에 온다.
  // 위쪽 절반 좌석은 카드가 스택의 맨 아래(바깥쪽=위)라서 -translate-y-full로 아래쪽 끝을 좌표에 맞추고,
  // 아래쪽 절반 좌석은 카드가 맨 위(바깥쪽=아래)라서 세로 이동 없이 위쪽 끝을 좌표에 맞춘다.
  const isUpperHalf = position.topPercent < 50;

  // 칩 더미가 없을 때(0개)도 숫자가 카드 쪽으로 딸려 올라가지 않도록, 항상 좌석 바깥쪽 끝에
  // 고정한다(self-end/self-start로 행의 정렬 기준과 반대쪽에 앵커링).
  // 핸드가 끝난 뒤(쇼다운 또는 폴드 종료)에만 이번 핸드의 손익을 +/-로 보여준다.
  const showNetChange = handEnded && player.netChipChange !== 0;

  const chipStackBlock = (
    <div className={`flex flex-col items-center gap-1 ${isUpperHalf ? 'self-start' : 'self-end'}`}>
      <ChipStack amount={player.chips} />
      <span className="text-sm font-semibold text-amber-200">{formatMoney(player.chips)}</span>
      {showNetChange && (
        <span className={`text-sm font-bold ${player.netChipChange > 0 ? 'text-sky-400' : 'text-red-500'}`}>
          {player.netChipChange > 0 ? '+' : ''}
          {formatMoney(player.netChipChange)}
        </span>
      )}
    </div>
  );

  // 보유칩 더미는 "플레이어 본인 기준 오른쪽"에 둔다. 테이블 중앙을 보고 앉아 있다고 하면,
  // 나(하단, 화면 위쪽을 바라봄)는 오른손이 화면 오른쪽이지만, 맞은편(상단, 화면 아래쪽을 바라봄)
  // 플레이어는 좌우가 뒤집혀서 오른손이 화면 왼쪽이 된다 — 그래서 위쪽 절반은 반대로(왼쪽) 배치한다.
  const chipStackOnLeft = isUpperHalf;

  return (
    <div
      className={`absolute flex -translate-x-1/2 flex-col items-center gap-2 ${isUpperHalf ? '-translate-y-full' : ''}`}
      style={{ left: `${position.leftPercent}%`, top: `${position.topPercent}%` }}
    >
      {/* scale은 앉아서 보는 원근감(나와 가까운 좌석은 크게, 먼 좌석은 작게)을 흉내낸다.
          transform-origin을 테이블 라인 쪽 끝(카드 쪽)에 둬서, 축소되어도 카드 위치는 그대로 유지된다. */}
      <div
        className={`flex gap-2 ${isUpperHalf ? 'items-end' : 'items-start'}`}
        style={{ transform: `scale(${position.scale})`, transformOrigin: isUpperHalf ? 'bottom center' : 'top center' }}
      >
        {chipStackOnLeft && chipStackBlock}
        <div className="flex flex-col items-center gap-2">
          {isUpperHalf ? (
            <>
              {info}
              {cards}
            </>
          ) : (
            <>
              {cards}
              {info}
            </>
          )}
        </div>
        {!chipStackOnLeft && chipStackBlock}
      </div>
    </div>
  );
}
