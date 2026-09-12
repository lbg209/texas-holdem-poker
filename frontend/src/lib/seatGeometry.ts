import type { PlayerView } from '../types/room';
import { computeSeatPositions } from '../components/table/computeSeatPositions';
import type { SeatPosition } from '../components/table/computeSeatPositions';

// 고정 좌석 수 — SeatLayout과 동일(Room.MAX_PLAYERS와 일치). 현재 인원수가 아니라 항상 이 값
// 기준으로 배치한다(고정 좌석제 — 빈자리도 자리를 차지한다).
const SEAT_COUNT = 6;

// SeatLayout과 동일한 회전 규칙(내 좌석을 항상 하단 고정)으로 특정 플레이어의 정보박스 좌표를
// 계산한다. 칩 이동 애니메이션처럼 좌석 배치 바깥(테이블 중앙 등)에서 특정 플레이어의 좌표가
// 필요할 때 쓴다. 회전 기준은 리스트 순서가 아니라 물리적 좌석 번호(seatIndex)다 — 빈자리가
// 끼어 있어도 좌석 배치가 SeatLayout과 항상 일치해야 하기 때문.
export function getRotatedSeatPosition(
  players: PlayerView[],
  myPlayerId: string | null,
  playerId: string,
): SeatPosition | null {
  const player = players.find((p) => p.id === playerId);
  if (!player) {
    return null;
  }

  const positions = computeSeatPositions(SEAT_COUNT);
  const me = players.find((p) => p.id === myPlayerId);
  // 아직 자리에 앉지 않은 관전자 시점에서는 회전하지 않고(0번 좌석이 하단) 그대로 보여준다.
  const mySeatIndex = me?.seatIndex ?? 0;
  const rotatedIndex = (player.seatIndex - mySeatIndex + SEAT_COUNT) % SEAT_COUNT;
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
