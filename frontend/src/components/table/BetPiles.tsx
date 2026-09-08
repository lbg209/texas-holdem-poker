import type { PlayerView } from '../../types/room';
import type { SeatPosition } from './computeSeatPositions';
import { getBetPilePosition, getCardPosition, getRotatedSeatPosition } from '../../lib/seatGeometry';
import { formatMoney } from '../../lib/formatMoney';
import { ChipStack } from './ChipStack';

interface BetPilesProps {
  players: PlayerView[];
  myPlayerId: string | null;
}

// 더미를 카드~중앙 40% 지점까지 끌어서, 좌석 옆 보유칩 더미와 겹쳐 헷갈리지 않게 테이블
// 안쪽으로 확실히 떨어뜨린다.
const PILE_FRACTION = 0.4;
// 금액 숫자는 더미 위/아래가 아니라 옆(중앙 쪽)에 붙인다 — 위쪽에 두면 카드와 겹칠 수 있어서.
const LABEL_SIDE_OFFSET = 9;
// 내 좌석처럼 leftPercent가 정확히 50이면 중앙 보간만으로는 좌우로 전혀 안 움직이므로,
// 보유칩 더미와 같은 방향(중앙 쪽)으로 살짝 더 밀어준다.
const HORIZONTAL_NUDGE = 6;

function inwardDirection(basePos: SeatPosition): 1 | -1 {
  return basePos.leftPercent <= 50 ? 1 : -1;
}

// 이번 스트리트에 낸 베팅액(currentRoundBet)을 좌석과 팟(중앙) 사이에 칩 더미로 보여준다.
// 스트리트가 끝나 팟으로 쓸려 들어갈 때는 FlyingChips가 같은 layoutId(`bet-pile-${playerId}`)를
// 가진 ChipStack을 팟 위치에 그려서, 이 더미가 그대로 이동하는 것처럼 보이게 이어받는다.
export function BetPiles({ players, myPlayerId }: BetPilesProps) {
  return (
    <>
      {players
        .filter((player) => player.currentRoundBet > 0)
        .map((player) => {
          const basePos = getRotatedSeatPosition(players, myPlayerId, player.id);
          if (!basePos) {
            return null;
          }
          const direction = inwardDirection(basePos);
          const cardPos = getCardPosition(basePos);
          const rawPilePos = getBetPilePosition(cardPos, PILE_FRACTION);
          const pilePos: SeatPosition = {
            ...rawPilePos,
            leftPercent: rawPilePos.leftPercent + direction * HORIZONTAL_NUDGE * basePos.scale,
          };
          // 금액 숫자는 더미와 같은 높이, 더미보다 더 중앙 쪽(옆)에 — 카드와 세로로 겹칠 일이 없다.
          const labelPos: SeatPosition = {
            leftPercent: pilePos.leftPercent + direction * LABEL_SIDE_OFFSET * pilePos.scale,
            topPercent: pilePos.topPercent,
            scale: pilePos.scale,
          };
          return (
            <div key={player.id}>
              <div
                className="pointer-events-none absolute z-20 -translate-x-1/2 -translate-y-1/2 scale-75"
                style={{ left: `${pilePos.leftPercent}%`, top: `${pilePos.topPercent}%` }}
              >
                <ChipStack amount={player.currentRoundBet} layoutId={`bet-pile-${player.id}`} />
              </div>
              {/* 액션 종류(CALL/RAISE 등)는 좌석 정보박스 라벨로 이미 보이니, 여기서는 금액만
                  더미 옆에 크게 보여줘서 한눈에 얼마인지 읽기 쉽게 한다. */}
              <span
                className="pointer-events-none absolute z-20 -translate-x-1/2 -translate-y-1/2 whitespace-nowrap rounded-full bg-slate-950/85 px-2 py-0.5 text-xs font-bold text-amber-200 shadow-md"
                style={{ left: `${labelPos.leftPercent}%`, top: `${labelPos.topPercent}%` }}
              >
                {formatMoney(player.currentRoundBet)}
              </span>
            </div>
          );
        })}
    </>
  );
}
