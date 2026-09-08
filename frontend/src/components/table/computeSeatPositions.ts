export interface SeatPosition {
  leftPercent: number;
  topPercent: number;
  // 앉아서 바라보는 원근감을 흉내내기 위한 크기 배율 — 나(하단)에 가까울수록 1에 가깝고,
  // 맞은편(상단)으로 갈수록 작아진다.
  scale: number;
}

// 나(하단, index 0)에 가까운 좌석은 넓게 퍼지고 크게, 맞은편(상단)으로 갈수록 좁게 모이고
// 작아지도록 해서 위에서 내려다보는 정원이 아니라 앞에 앉아서 보는 느낌(가짜 원근감)을 낸다.
const RX_NEAR = 46;
const RX_FAR = 24;
const RY = 38;
const SCALE_NEAR = 1;
const SCALE_FAR = 0.7;

// count명을 타원형으로 배치한다. index 0은 항상 하단 중앙이고, index가 커질수록 시계방향으로 이동한다.
// 실제 "내 좌석을 하단에 고정"하는 회전은 호출 측(SeatLayout)에서 rotatedIndex를 넘겨서 처리한다.
export function computeSeatPositions(count: number): SeatPosition[] {
  if (count <= 0) {
    return [];
  }

  const positions: SeatPosition[] = [];
  for (let i = 0; i < count; i++) {
    const angleDeg = 90 + (360 / count) * i;
    const angleRad = (angleDeg * Math.PI) / 180;
    const sin = Math.sin(angleRad);
    const cos = Math.cos(angleRad);

    // depth: 0(하단, 나와 가장 가까움) ~ 1(상단, 가장 멀리 있음)
    const depth = (1 - sin) / 2;
    const rx = RX_NEAR - (RX_NEAR - RX_FAR) * depth;
    const scale = SCALE_NEAR - (SCALE_NEAR - SCALE_FAR) * depth;

    positions.push({
      leftPercent: 50 + rx * cos,
      topPercent: 50 + RY * sin,
      scale,
    });
  }
  return positions;
}
