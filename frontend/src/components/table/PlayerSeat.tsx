import type { CardView, PlayerView, ShowdownHandView } from '../../types/room';
import type { SeatPosition } from './computeSeatPositions';
import type { Position } from '../../lib/positions';
import { formatMoney } from '../../lib/formatMoney';
import { containsCard, getHandRankLabel, isSpecialHandRank } from '../../lib/handRank';
import { Card } from './Card';
import { RevealCard } from './RevealCard';
import { ChipStack } from './ChipStack';

// 보유칩 더미는 정보박스 옆(중앙 쪽)에, 고정된 만큼 떨어진 자리에 둔다.
const CHIP_HORIZONTAL_OFFSET = 17;

interface PlayerSeatProps {
  player: PlayerView;
  // 정보박스(닉네임/액션/족보)의 좌표. 카드/보유칩은 여기서 고정된 만큼 떨어진 위치로 계산한다
  // (카드는 바로 위, 칩은 중앙 쪽 옆) — "정보박스 위치는 지금 좋다"는 피드백에 맞춰, 이 좌표
  // 하나만 조정하면 카드/칩도 같이 따라오게 만들었다.
  position: SeatPosition;
  tablePosition: Position | null;
  isMe: boolean;
  isCurrentActor: boolean;
  showCards: boolean;
  isChipLeader: boolean;
  showdownHand: ShowdownHandView | null;
  winnerCards: CardView[];
  isWinner: boolean;
  handEnded: boolean;
  // 새 핸드 딜링 중일 때만 주어진다(0~2). 주어지면 실제 홀카드 데이터 대신 이 개수만큼 뒷면
  // 카드 자리만 보여준다 — 상대 카드는 서버가 애초에 안 보내주므로 실제 데이터로는 표현할 수 없다.
  dealtPlaceholderCount?: number;
}

// 액션 줄: FOLD/ALL-IN은 상태 기준(스트리트가 바뀌어도 유지), CHECK/CALL/BET/RAISE는
// 이번 스트리트의 lastAction 기준(스트리트 전환 시 서버에서 null로 리셋됨)으로 판단한다.
function getActionLine(player: PlayerView): string | null {
  if (player.status === 'BUSTED') {
    return 'BUSTED';
  }
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

// 카드가 막 나타난 순간(0장 -> 실제 카드)에는 뒷면에서 앞면으로 뒤집히는 연출을 재생한다.
// (상대는 쇼다운 공개 때, 나는 새 핸드 딜링이 끝나고 확인할 때 — 둘 다 이 컴포넌트가 처음
// 실제 카드값을 받는 순간이라 자연히 한 번만 재생된다.) 아직 카드가 없으면 뒷면 그대로 둔다.
function HoleCard({ card, faceUp, highlighted }: { card?: CardView; faceUp: boolean; highlighted: boolean }) {
  if (card) {
    return <RevealCard card={card} highlighted={highlighted} flipDelayMs={150} flipDurationMs={500} />;
  }
  return <Card card={card} face={faceUp ? 'up' : 'down'} highlighted={highlighted} />;
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
  isWinner,
  handEnded,
  dealtPlaceholderCount,
}: PlayerSeatProps) {
  const [hole0, hole1] = player.holeCards;
  const faceUp = player.holeCards.length > 0;
  const actionLine = getActionLine(player);
  const isSpecialHand = showdownHand ? isSpecialHandRank(showdownHand.handRank) : false;
  const isBusted = player.status === 'BUSTED';

  // 파산한 좌석은 이번 핸드에 아예 참여하지 않으므로 카드를 아예 그리지 않는다 — 홀카드가
  // 항상 비어 있어(백엔드가 애초에 안 나눠줌) HoleCard의 "아직 안 왔으면 뒷면 표시" 기본 동작을
  // 그대로 두면, 딜링 애니메이션이 끝난 뒤에도 뒷면 카드 두 장이 계속 남아 마치 참여 중인
  // 것처럼 보이는 문제가 있었다.
  const cards = isBusted
    ? null
    : // 딜링 중이면(dealtPlaceholderCount가 주어짐) 실제 홀카드 데이터는 무시하고 그 개수만큼
      // 뒷면 카드만 보여준다. 그 외엔 평소대로(하이라이트는 "이긴 조합"만 — 승자의 bestFive에
      // 실제로 포함된 카드만 강조).
      dealtPlaceholderCount !== undefined ? (
        <div className="flex gap-1">
          {Array.from({ length: dealtPlaceholderCount }).map((_, i) => (
            <Card key={i} face="down" />
          ))}
        </div>
      ) : (
        showCards && (
          <div className="flex gap-1">
            <HoleCard card={hole0} faceUp={faceUp} highlighted={!!(hole0 && containsCard(winnerCards, hole0))} />
            <HoleCard card={hole1} faceUp={faceUp} highlighted={!!(hole1 && containsCard(winnerCards, hole1))} />
          </div>
        )
      );

  // 핸드가 끝난 뒤(쇼다운 또는 폴드 종료)에만 이번 핸드의 손익을 +/-로 보여준다.
  const showNetChange = handEnded && player.netChipChange !== 0;
  // 이번 핸드 누적 베팅액은 정보박스의 별도 줄 대신, 중앙 팟의 "+금액" 표시와 같은 방식으로
  // 칩 스택 숫자 옆에 "-금액"으로 붙인다. 핸드가 끝나면(손익 +/-로 대체되므로) 더는 보여주지 않는다.
  const showContribution = !handEnded && player.totalHandContribution > 0;

  // 보유칩 더미는 정보박스 옆(중앙 쪽)에 둔다. 좌석이 테이블 왼쪽(leftPercent<=50, 예: 10시
  // 방향)이면 정보박스 오른쪽(중앙 쪽)에, 오른쪽(1시 방향)이면 정보박스 왼쪽(중앙 쪽)에.
  // 내 좌석(하단 정중앙, leftPercent=50)도 오른쪽에 온다.
  const chipOnRight = position.leftPercent <= 50;
  const chipPosition: SeatPosition = {
    leftPercent: position.leftPercent + (chipOnRight ? 1 : -1) * CHIP_HORIZONTAL_OFFSET * position.scale,
    topPercent: position.topPercent,
    scale: position.scale,
  };

  return (
    <>
      {/* 카드 + 정보박스: 같은 컨테이너에 묶어서, 카드는 정보박스의 실제 DOM 위치를 기준으로
          "바로 위, 수평 중앙"에 절대배치한다 — 좌표를 따로 계산해서 둘 다 배치하면 미세하게
          어긋날 여지가 있어서, 카드는 정보박스와 같은 부모를 공유하게 만들어 어긋날 수 없게 했다. */}
      <div
        className="absolute"
        style={{
          left: `${position.leftPercent}%`,
          top: `${position.topPercent}%`,
          transform: `translate(-50%, -50%) scale(${position.scale})`,
        }}
      >
        <div className="relative">
          <div className="absolute bottom-full left-1/2 mb-2 -translate-x-1/2">{cards}</div>
          <div
            className={`relative flex min-w-28 flex-col items-center rounded px-2 py-1 text-xs shadow-md sm:text-sm ${
              player.status === 'BUSTED'
                ? // 파산: 폴드(단순 반투명)와 다르게, 회색조 + 붉은 빗금 균열 무늬 + 점선 테두리로
                  // "깨진" 느낌을 낸다 — 더는 이번 핸드에 존재하지 않는 좌석임을 한눈에 구분되게.
                  'grayscale border-2 border-dashed border-red-900/60 bg-slate-950/90 bg-[repeating-linear-gradient(135deg,rgba(127,29,29,0.35)_0px,rgba(127,29,29,0.35)_2px,transparent_2px,transparent_10px)] opacity-70'
                : isWinner
                  ? 'border-2 border-yellow-400 bg-gradient-to-b from-yellow-900/60 to-slate-900 shadow-[0_0_12px_rgba(250,204,21,0.6)]'
                  : isCurrentActor
                    ? 'border-2 border-emerald-400 bg-gradient-to-b from-slate-700 to-slate-900 shadow-emerald-400/40'
                    : 'border border-slate-600 bg-gradient-to-b from-slate-700/90 to-slate-900/90'
            } ${player.status === 'FOLDED' ? 'opacity-50' : ''}`}
          >
            <span className={`font-medium ${isMe ? 'text-emerald-400' : 'text-slate-100'}`}>
              {isChipLeader && <span title="칩리더">👑</span>}
              {player.nickname}
              {tablePosition && (
                <span className="ml-1 rounded bg-slate-200 px-1 text-[10px] text-slate-900">{tablePosition}</span>
              )}
            </span>
            {actionLine && (
              <span className={player.status === 'BUSTED' ? 'font-bold text-red-500' : 'text-amber-300'}>
                {actionLine}
              </span>
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
        </div>
      </div>

      {/* 보유칩 더미: 정보박스 옆(중앙 쪽). */}
      <div
        className="absolute"
        style={{
          left: `${chipPosition.leftPercent}%`,
          top: `${chipPosition.topPercent}%`,
          transform: `translate(-50%, -50%) scale(${chipPosition.scale})`,
        }}
      >
        <div className="flex flex-col items-center gap-1">
          <ChipStack amount={player.chips} />
          <span className="text-sm font-semibold text-amber-200">
            {formatMoney(player.chips)}
            {showContribution && <span className="text-red-400"> -{formatMoney(player.totalHandContribution)}</span>}
          </span>
          {showNetChange && (
            <span className={`text-sm font-bold ${player.netChipChange > 0 ? 'text-sky-400' : 'text-red-500'}`}>
              {player.netChipChange > 0 ? '+' : ''}
              {formatMoney(player.netChipChange)}
            </span>
          )}
        </div>
      </div>
    </>
  );
}
