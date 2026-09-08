import type { PlayerView } from '../types/room';
import { computeSeatPositions } from '../components/table/computeSeatPositions';
import type { SeatPosition } from '../components/table/computeSeatPositions';

// SeatLayout과 동일한 회전 규칙(내 좌석을 항상 하단 고정)으로 특정 플레이어의 정보박스 좌표를
// 계산한다. 칩 이동 애니메이션처럼 좌석 배치 바깥(테이블 중앙 등)에서 특정 플레이어의 좌표가
// 필요할 때 쓴다.
export function getRotatedSeatPosition(
  players: PlayerView[],
  myPlayerId: string | null,
  playerId: string,
): SeatPosition | null {
  const count = players.length;
  if (count === 0) {
    return null;
  }

  const positions = computeSeatPositions(count);
  const myIndex = players.findIndex((p) => p.id === myPlayerId);
  const i = players.findIndex((p) => p.id === playerId);
  if (i === -1) {
    return null;
  }

  const rotatedIndex = myIndex === -1 ? i : (i - myIndex + count) % count;
  return positions[rotatedIndex] ?? null;
}

// 카드는 정보박스 "바로 위"(화면상 topPercent가 더 작은 쪽)에 고정된 만큼 떨어진 자리에 둔다 —
// PlayerSeat와 BetPiles/DealingCards 등 여러 곳에서 "카드가 실제로 있는 자리"가 같아야 해서
// 이 계산을 한 곳(seatGeometry)에 모아둔다.
const CARD_VERTICAL_OFFSET = 14;

export function getCardPosition(base: SeatPosition): SeatPosition {
  return {
    leftPercent: base.leftPercent,
    topPercent: base.topPercent - CARD_VERTICAL_OFFSET * base.scale,
    scale: base.scale,
  };
}

// 카드(또는 다른 기준) 좌표와 테이블 중앙(팟) 사이를 보간해서, 베팅 중인 칩 더미를 놓을 위치를
// 계산한다. fraction이 0이면 기준 좌표 그대로, 1이면 정중앙(팟 위치)이다.
export function getBetPilePosition(seatPos: SeatPosition, fraction = 0.35): SeatPosition {
  return {
    leftPercent: seatPos.leftPercent + (50 - seatPos.leftPercent) * fraction,
    topPercent: seatPos.topPercent + (50 - seatPos.topPercent) * fraction,
    scale: seatPos.scale,
  };
}
