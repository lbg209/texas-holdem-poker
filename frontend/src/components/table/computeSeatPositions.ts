export interface SeatPosition {
  leftPercent: number;
  topPercent: number;
  // 앉아서 바라보는 원근감을 흉내내기 위한 크기 배율 — 나(하단)에 가까울수록 1에 가깝고,
  // 맞은편(상단)으로 갈수록 작아진다.
  scale: number;
}

// 나(하단, index 0)에 가까운 좌석은 넓게 퍼지고 크게, 맞은편(상단)으로 갈수록 좁게 모이고
// 작아지도록 해서 위에서 내려다보는 정원이 아니라 앞에 앉아서 보는 느낌(가짜 원근감)을 낸다.
// 이 좌표는 정보박스(닉네임/액션/족보)의 기준점이다 — 카드/보유칩은 여기서 고정된 만큼
// 떨어진 위치로 계산한다(seatGeometry.ts의 getCardPosition 등 참고).
const RX_NEAR = 62;
const RX_FAR = 52;
const RY = 58;
// 정확히 맞은편(12시, sin=-1)에 오는 좌석 — 1:1의 상대, 4/6인 게임의 맨 위 좌석 등 — 은
// RY를 그대로 곱하면 topPercent가 음수(화면 위로 벗어남)까지 나온다. 위쪽으로는 이 값 밑으로
// 못 내려가게(=화면에서 이 정도 아래로는 내려오게) 막는다. 다른 각도의 좌석은 원래도 이 값보다
// 여유가 있어서 영향받지 않는다.
const MIN_INFO_TOP_PERCENT = 6;
// 내 카드(하단, 화면과 가장 가까움)가 다른 좌석보다 원래도 커 보이는데, 지금 크기가 너무
// 크다는 피드백이 있어 SCALE_NEAR를 1보다 낮췄다.
const SCALE_NEAR = 0.85;
// 상대(맞은편에 가까운 좌석)의 정보박스가 너무 작다는 피드백으로 SCALE_FAR를 올렸다 — depth=0(내
// 좌석)에는 SCALE_NEAR만 적용되므로, 이 값만 올리면 내 좌석 크기는 그대로 두고 먼 좌석만 커진다.
const SCALE_FAR = 0.68;

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

    // 소수점 3자리로 반올림한다 — 예를 들어 내 좌석(index 0)은 각도가 정확히 90도라
    // Math.cos(90°)가 부동소수점 오차로 정확히 0이 아니라 아주 미세한 값(예: 6e-17)이 나오는데,
    // 반올림 없이 그대로 두면 leftPercent가 "50.000000000000004"처럼 되어 "<=50이면 오른쪽" 같은
    // 좌우 판단이 뒤집혀버린다.
    const topPercent = Math.max(50 + RY * sin, MIN_INFO_TOP_PERCENT);

    positions.push({
      leftPercent: Math.round((50 + rx * cos) * 1000) / 1000,
      topPercent: Math.round(topPercent * 1000) / 1000,
      scale,
    });
  }
  return positions;
}
